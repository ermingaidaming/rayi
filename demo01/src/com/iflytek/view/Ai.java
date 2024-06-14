package com.iflytek.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.iflytek.cloud.speech.SpeechConstant;
import com.iflytek.cloud.speech.SpeechRecognizer;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.*;
import com.formdev.flatlaf.FlatDarkLaf;
import com.iflytek.cloud.speech.*;
import com.iflytek.util.JsonParser;
import com.iflytek.util.Version;
import okhttp3.*;




/*
 * Created by JFormDesigner on Fri May 24 10:26:42 CST 2024
 */



/**
 * @author 24478
 */
public class Ai extends JFrame {
    //大模型参数
    public static final String hostUrl = "https://spark-api.xf-yun.com/v3.5/chat";

    public static final String appid = "03306e8f";
    public static final String apiSecret = "NmQ2MDg4YjM5NjVmNWJjYjc5NzU1N2U0";
    public static final String apiKey = "fe9641504caac66fe09bbb6a114aedab";

    public  String totalAnswer=""; //大模型的答案汇总
    public  String NewQuestion = "";//每次提问的问题字符串
    private Boolean wsCloseFlag; //是否关闭连接的标志
    // 语音听写对象
    private SpeechRecognizer mIat;
    // 语音合成对象
    private SpeechSynthesizer mTts;


    //配置参数map
    private Map<String, String> mParamMap = new HashMap<String, String>();
    private Map<String, String[]> mVoiceMap = new LinkedHashMap<String, String[]>();

    private static class DefaultValue{
        public static final String ENG_TYPE = SpeechConstant.TYPE_CLOUD;
        public static final String SPEECH_TIMEOUT = "60000";
        public static final String NET_TIMEOUT = "20000";
        public static final String LANGUAGE = "zh_cn";

        public static final String ACCENT = "mandarin";
        public static final String DOMAIN = "iat";
        public static final String VAD_BOS = "5000";
        public static final String VAD_EOS = "1800";

        public static final String RATE = "16000";
        public static final String NBEST = "1";
        public static final String WBEST = "1";
        public static final String PTT = "1";

        public static final String RESULT_TYPE = "json";
        public static final String SAVE = "0";
    }

    private void initParamMap(){
        this.mParamMap.put( SpeechConstant.ENGINE_TYPE, Ai.DefaultValue.ENG_TYPE );
        this.mParamMap.put( SpeechConstant.SAMPLE_RATE, Ai.DefaultValue.RATE );
        this.mParamMap.put( SpeechConstant.NET_TIMEOUT, Ai.DefaultValue.NET_TIMEOUT );
        this.mParamMap.put( SpeechConstant.KEY_SPEECH_TIMEOUT, Ai.DefaultValue.SPEECH_TIMEOUT );

        this.mParamMap.put( SpeechConstant.LANGUAGE, Ai.DefaultValue.LANGUAGE );
        this.mParamMap.put( SpeechConstant.ACCENT, Ai.DefaultValue.ACCENT );
        this.mParamMap.put( SpeechConstant.DOMAIN, Ai.DefaultValue.DOMAIN );
        this.mParamMap.put( SpeechConstant.VAD_BOS, Ai.DefaultValue.VAD_BOS );

        this.mParamMap.put( SpeechConstant.VAD_EOS, Ai.DefaultValue.VAD_EOS );
        this.mParamMap.put( SpeechConstant.ASR_NBEST, Ai.DefaultValue.NBEST );
        this.mParamMap.put( SpeechConstant.ASR_WBEST, Ai.DefaultValue.WBEST );
        this.mParamMap.put( SpeechConstant.ASR_PTT, Ai.DefaultValue.PTT );

        this.mParamMap.put( SpeechConstant.RESULT_TYPE, Ai.DefaultValue.RESULT_TYPE );
        this.mParamMap.put( SpeechConstant.ASR_AUDIO_PATH, null );
    }

public Ai() {
    initComponents();
    // 初始化听写对象
    mIat=SpeechRecognizer.createRecognizer();
    //初始化参数变量
    initParamMap();


}
    /**
     * websocket监听器 ： 来实现对websocket动作的 回调处理；
     */
    private class BigModelNew extends WebSocketListener {

        public Gson gson = new Gson();
        public List<RoleContent> historyList=new ArrayList<>(); // 对话历史存储集合
        public  boolean canAddHistory(){  // 由于历史记录最大上线1.2W左右，需要判断是能能加入历史
            int history_length=0;
            for(RoleContent temp:historyList){
                history_length=history_length+temp.content.length();
            }
            if(history_length>12000){
                historyList.remove(0);
                historyList.remove(1);
                historyList.remove(2);
                historyList.remove(3);
                historyList.remove(4);
                return false;
            }else{
                return true;
            }
        }

        // 线程来发送音频与参数
        class MyThread extends Thread {
            private WebSocket webSocket;
            public MyThread(WebSocket webSocket) {
                this.webSocket = webSocket;
            }

            public void run() {
                try {
                    JSONObject requestJson=new JSONObject();
                    JSONObject header=new JSONObject();  // header参数
                    header.put("app_id", appid);
                    header.put("uid",UUID.randomUUID().toString().substring(0, 10));

                    JSONObject parameter=new JSONObject(); // parameter参数
                    JSONObject chat=new JSONObject();
                    chat.put("domain","generalv3.5");
                    chat.put("temperature",0.5);
                    chat.put("max_tokens",4096);
                    parameter.put("chat",chat);

                    JSONObject payload=new JSONObject(); // payload参数
                    JSONObject message=new JSONObject();
                    JSONArray text=new JSONArray();

                    // 历史问题获取
                    if(historyList.size()>0){
                        for(RoleContent tempRoleContent:historyList){
                            text.add(JSON.toJSON(tempRoleContent));
                        }
                    }

                    // 最新问题
                    RoleContent roleContent=new RoleContent();
                    roleContent.role="user";
                    roleContent.content=NewQuestion;//每次提问的问题
                    text.add(JSON.toJSON(roleContent));
                    historyList.add(roleContent);

                    message.put("text",text);
                    payload.put("message",message);

                    requestJson.put("header",header);
                    requestJson.put("parameter",parameter);
                    requestJson.put("payload",payload);

                    webSocket.send(requestJson.toString());
                    // 等待服务端返回完毕后关闭
                    while (true) {
                        Thread.sleep(200);
                        if (wsCloseFlag) {
                            break;
                        }
                    }
                    webSocket.close(1000, "");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            System.out.print("大模型连接");
            wsCloseFlag = false;
            MyThread myThread = new MyThread(webSocket);
            myThread.start();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {

            JsonParse myJsonParse = gson.fromJson(text, JsonParse.class);
            if (myJsonParse.header.code != 0) {
                System.out.println("发生错误，错误码为：" + myJsonParse.header.code);
                System.out.println("本次请求的sid为：" + myJsonParse.header.sid);
                webSocket.close(1000, "");
            }
            List<Text> textList = myJsonParse.payload.choices.text;
            for (Text temp : textList) {
                System.out.print(temp.content);
                //把结果显示到 区域
                textArea1.append(temp.content);
                totalAnswer=totalAnswer+temp.content;
            }
            if (myJsonParse.header.status == 2) {
                // 可以关闭连接，释放资源
                System.out.println();
                System.out.println("***********************************************");
                if(canAddHistory()){
                    RoleContent roleContent=new RoleContent();
                    roleContent.setRole("assistant");
                    roleContent.setContent(totalAnswer);
                    historyList.add(roleContent);
                }else{
                    historyList.remove(0);
                    RoleContent roleContent=new RoleContent();
                    roleContent.setRole("assistant");
                    roleContent.setContent(totalAnswer);
                    historyList.add(roleContent);
                }
                //是最后一帧 说明可以关闭连接
                wsCloseFlag = true;
                ttsSpeechView.TtsPlayVoice(totalAnswer);

            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            try {
                if (null != response) {
                    int code = response.code();
                    System.out.println("onFailure code:" + code);
                    System.out.println("onFailure body:" + response.body().string());
                    if (101 != code) {
                        System.out.println("connection failed");
                        System.exit(0);
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        //返回的json结果拆解
        class JsonParse {
            Header header;
            Payload payload;
        }

        class Header {
            int code;
            int status;
            String sid;
        }

        class Payload {
            Choices choices;
        }

        class Choices {
            List<Text> text;
        }

        class Text {
            String role;
            String content;
        }
        class RoleContent{
            String role;
            String content;

            public String getRole() {
                return role;
            }

            public void setRole(String role) {
                this.role = role;
            }

            public String getContent() {
                return content;
            }

            public void setContent(String content) {
                this.content = content;
            }
        }
    }


    /**
     * 听写监听器 ： 来实现对听写动作的 回调处理；
     */
    private RecognizerListener recognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            button1.setText("听写中...");
            button1.setEnabled(false);
        }

        @Override
        public void onEndOfSpeech() {
        }


        /**
         * 获取听写结果. 获取RecognizerResult类型的识别结果，并对结果进行累加，显示到Area里
         */
        @Override
        public void onResult(RecognizerResult results, boolean islast) {
            //如果要解析json结果，请考本项目示例的 com.iflytek.util.JsonParser类
            String text = JsonParser.parseIatResult(results.getResultString());
            //String text = results.getResultString();
            textArea2.append(text);
            //识别结果放在问题上
            NewQuestion = NewQuestion+text;
            if( islast ){
                iatSpeechInitUI();
                //识别完之后 开始调用大模型

                //构建鉴权url  建立websocket连接
                String authUrl = null;
                try {
                    authUrl = getAuthUrl(hostUrl, apiKey, apiSecret);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                OkHttpClient client = new OkHttpClient.Builder().build();
                String url = authUrl.toString().replace("http://", "ws://").replace("https://", "wss://");
                Request request = new Request.Builder().url(url).build();
                totalAnswer="";
                WebSocket webSocket = client.newWebSocket(request, new BigModelNew()); //创建的监听器
            }
        }

        @Override
        public void onVolumeChanged(int volume) {
//            if (volume == 0)
//                volume = 1;
//            else if (volume >= 6)
//                volume = 6;
//            //labelWav.setIcon(new ImageIcon("res/mic_0" + volume + ".png"));
        }

        @Override
        public void onError(SpeechError error) {
            if (null != error){
                //出错的错误码显示到 区域
                textArea2.setText( error.getErrorDescription(true) );
                iatSpeechInitUI();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int agr2, String msg) {
            //以下代码用于调试，如果出现问题可以将sid提供给讯飞开发者，用于问题定位排查
			/*if(eventType == SpeechEvent.EVENT_SESSION_ID) {
				DebugLog.Log("sid=="+msg);
			}*/
        }
    };

    /**
     * 听写结束，恢复初始状态
     */
    public void iatSpeechInitUI() {
        button1.setText("开始");
        button1.setEnabled(true);
    }


    //配置信息写入 进行一堆录音参数的设置，mIat.setParameter
    void setting(){
        final String engType = this.mParamMap.get(SpeechConstant.ENGINE_TYPE);

        for( Map.Entry<String, String> entry : this.mParamMap.entrySet() ){
            mIat.setParameter( entry.getKey(), entry.getValue() );
        }

        //本地识别时设置资源，并启动引擎
        if( SpeechConstant.TYPE_LOCAL.equals(engType) ){
            //启动合成引擎
            mIat.setParameter( ResourceUtil.ENGINE_START, SpeechConstant.ENG_ASR );
            //设置资源路径
            final String rate = this.mParamMap.get( SpeechConstant.SAMPLE_RATE );
            final String tag = rate.equals("16000") ? "16k" : "8k";
            String curPath = System.getProperty("user.dir");
            String resPath = ResourceUtil.generateResourcePath( curPath+"/asr/common.jet" )
                    + ";" + ResourceUtil.generateResourcePath( curPath+"/asr/src_"+tag+".jet" );
            System.out.println( "resPath="+resPath );
            mIat.setParameter( ResourceUtil.ASR_RES_PATH, resPath );
        }// end of if is TYPE_LOCAL

    }// end of function setting


    //开始录音按键
    private void button1MouseClicked(MouseEvent e) {
        setting();
        textArea2.setText( "我:" );
        if (!mIat.isListening())
            mIat.startListening(recognizerListener);
        else
            mIat.stopListening();
        //TODO add your code here
    }

    public static void main(String[] args) {
        //使用SpeechUtility.createUtility()设置appid
        StringBuffer param = new StringBuffer();
        param.append( "appid=" + Version.getAppid() );
        SpeechUtility.createUtility( param.toString() );

        FlatDarkLaf.install();//暗黑主题
        JFrame frame = new Ai();
        //界面绘制
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    }
    public static String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        // 时间
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        // 拼接
        String preStr = "host: " + url.getHost() + "\n" +
                "date: " + date + "\n" +
                "GET " + url.getPath() + " HTTP/1.1";
        // System.err.println(preStr);
        // SHA256加密
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "hmacsha256");
        mac.init(spec);

        byte[] hexDigits = mac.doFinal(preStr.getBytes(StandardCharsets.UTF_8));
        // Base64加密
        String sha = Base64.getEncoder().encodeToString(hexDigits);
        // System.err.println(sha);
        // 拼接
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        // 拼接地址
        HttpUrl httpUrl = Objects.requireNonNull(HttpUrl.parse("https://" + url.getHost() + url.getPath())).newBuilder().//
                addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8))).//
                addQueryParameter("date", date).//
                addQueryParameter("host", url.getHost()).//
                build();

        System.out.println(httpUrl.toString());
        return httpUrl.toString();
    }
    private SynthesizerListener mSynListener = new SynthesizerListener() {

		@Override
		public void onSpeakBegin() {
		}

		@Override
		public void onBufferProgress(int progress, int beginPos, int endPos,
				String info) {

		}

		@Override
	     public void onSpeakPaused() {

		}

		@Override
		public void onSpeakResumed() {

		}

		@Override
		public void onSpeakProgress(int progress, int beginPos, int endPos) {

		}

		@Override
		public void onCompleted(SpeechError error) {

		}


		@Override
		public void onEvent(int eventType, int arg1, int arg2, int arg3, Object obj1, Object obj2) {



		}
        void initVoiceMap(){}
        private void initParamMap(){}
        void setting(){}
        //直接用这个来播放语音
        public void TtsPlayVoice(String mText) {
            mTts.startSpeaking(mText, mSynListener);
        }

        };







    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents  @formatter:off
        panel1 = new JPanel();
        textArea1 = new JTextArea();
        panel2 = new JPanel();
        textArea2 = new JTextArea();
        button1 = new JButton();

        //======== this ========
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== panel1 ========
        {
            panel1.setLayout(new GridLayout());
            panel1.add(textArea1);
        }
        contentPane.add(panel1, BorderLayout.CENTER);

        //======== panel2 ========
        {
            panel2.setBorder(new EtchedBorder());
            panel2.setAlignmentX(20.0F);
            panel2.setAlignmentY(2.0F);
            panel2.setMinimumSize(new Dimension(119, 34));
            panel2.setPreferredSize(new Dimension(119, 34));
            panel2.setLayout(new BorderLayout());

            //---- textArea2 ----
            textArea2.setText("\u6211\uff1a");
            panel2.add(textArea2, BorderLayout.CENTER);

            //---- button1 ----
            button1.setText("\u5f00\u59cb");
            button1.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    button1MouseClicked(e);
                }
            });
            panel2.add(button1, BorderLayout.LINE_END);
        }
        contentPane.add(panel2, BorderLayout.SOUTH);
        setSize(390, 350);
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents  @formatter:on
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables  @formatter:off
    private JPanel panel1;
    private JTextArea textArea1;
    private JPanel panel2;
    private JTextArea textArea2;
    private JButton button1;
    // JFormDesigner - End of variables declaration  //GEN-END:variables  @formatter:on
}
