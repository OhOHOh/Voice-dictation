package com.yu.voiceassistanttest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUnderstander;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ContactManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String KEY_INDEX = "index";
    private static final String KEY_BOOLEAN = "iboolean";

    private SpeechRecognizer mIat;
    // 语义理解对象（语音到语义）。
    private SpeechUnderstander mSpeechUnderstander;
    // 语音合成对象
    SpeechSynthesizer mTts;
    // UI 界面
    private RecognizerDialog mIatDialog;
    private Toast mToast;
    //private TextView mUnderstanderText;
    //private Button contact;  //上传联系人的 Button

    // 消息存储
    private List<Msg> msgList = new ArrayList<>();
    private RecyclerView msgRecyclerView;
    private MsgAdapter msgAdapter;
    private boolean isUploadContacts = false;

    // Android 6.0 中权限授予
    String phoneNumberG = "";
    public static final String[] project = new String[] {
            "_id", "number", "name"
    };
    public static final Uri callLogUri = CallLog.Calls.CONTENT_URI;
    public static final String selection = "duration >= ?"+"and type = ?";
    //这是条件中的替换selection 中？的值
    public static final String[] selectionArgs = new String[]{"0","2"};
    //这是查询结果显示的顺序，顺序有二种：ASC为升序，DESC为降序
    public static final String sortOrder = "date DESC";
    public final int MY_PERMISSIONS_REQUEST_READ_CALL_LOG = 100;





    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //隐藏标题栏
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SpeechUtility.createUtility(this, SpeechConstant.APPID+"=58eddecc");

        initMsgs();   //初始化消息, 欢迎界面

        // 屏幕翻转时 保存 msgList 中的数据
        if (savedInstanceState != null) {
            msgList = (ArrayList<Msg>) savedInstanceState.getSerializable(KEY_INDEX);
            isUploadContacts = savedInstanceState.getBoolean(KEY_BOOLEAN);
        }
        if (!isUploadContacts) {
            //上传联系人
            Toast.makeText(this, "isUpdateContacts is false", Toast.LENGTH_SHORT).show();
            ContactManager mgr = ContactManager.createManager(MainActivity.this, mContactListener);
            mgr.asyncQueryAllContactsName();
            isUploadContacts = true;
        }


        msgRecyclerView = (RecyclerView) findViewById(R.id.msg_recycle_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        msgAdapter = new MsgAdapter(msgList);
        msgRecyclerView.setLayoutManager(layoutManager);
        msgRecyclerView.setAdapter(msgAdapter);

        //初始化对象
        mIat = SpeechRecognizer.createRecognizer(this, mInitListener); //上传联系人要用
        mIatDialog = new RecognizerDialog(this, mInitListener);     //UI 界面要用
        mTts = SpeechSynthesizer.createSynthesizer(MainActivity.this, new InitListener() {
            @Override
            public void onInit(int code) {
                Log.d(TAG, "InitListener init() code = " + code);
                if (code != ErrorCode.SUCCESS) {
                    showTip("初始化失败,错误码："+code);
                }
                // else
                // 初始化成功，之后可以调用startSpeaking方法
                // 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
                // 正确的做法是将onCreate中的startSpeaking调用移至这里

            }
        }); //语音合成
        mSpeechUnderstander = SpeechUnderstander.createUnderstander(this, mSpeechUdrInitListener);
        mToast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);
        ImageButton btn = (ImageButton)findViewById(R.id.start_understander);//语义理解的 Button
        //contact = (Button)findViewById(R.id.contact);
        //mUnderstanderText = (TextView) findViewById(R.id.understander_text);
        //使得 TextView 可以垂直滑动来获得内容
        //mUnderstanderText.setMovementMethod(ScrollingMovementMethod.getInstance());

        //按钮的点击事件
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v)
            {

                //网页显示
                LinearLayout main = (LinearLayout)findViewById(R.id.root);
                ViewGroup.LayoutParams lp = main.getLayoutParams();
                lp.width = 0;
                lp.height = 0;
                main.setLayoutParams(lp);
                main.removeAllViews();

                //mUnderstanderText.setText("");

                //设置参数, 带 UI 界面的, 使用的是 RecognizerDialog 类
                // 设置听写引擎
                mIatDialog.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
                // 设置返回结果格式
                mIatDialog.setParameter(SpeechConstant.ASR_SCH, "1"); //设置为语义理解模式
                mIatDialog.setParameter(SpeechConstant.DOMAIN, "iat");
                mIatDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
                mIatDialog.setParameter(SpeechConstant.ACCENT, "mandarin");
                mIatDialog.setParameter(SpeechConstant.NLP_VERSION, "2.0"); //支持返回结果为 JSON 模式
                mIatDialog.setParameter(SpeechConstant.RESULT_TYPE, "json");
                // 设置回调接口
                mIatDialog.setListener(mRecognizerDialogListener);
                // 显示听写对话框
                mIatDialog.show();
                showTip(getString(R.string.talk_begin));
//                /*
//                 * 使用不带 UI 的界面, 即使用 SpeechUnderstander
//                 * 正宗的语义理解!
//                 *
//                 //设置参数
//                 mSpeechUnderstander.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
//                 mSpeechUnderstander.setParameter(SpeechConstant.ACCENT, "mandarin");
//                 mSpeechUnderstander.setParameter(SpeechConstant.NLP_VERSION, "2.0");
//                 mSpeechUnderstander.setParameter(SpeechConstant.RESULT_TYPE, "json");
//                 mSpeechUnderstander.setParameter(SpeechConstant.DOMAIN, "iat");
//                 // 开始前检查状态
//                 if (mSpeechUnderstander.isUnderstanding()) {
//                    mSpeechUnderstander.stopUnderstanding();
//                    showTip("停止录音，未准备好");
//                 } else {
//                    int ret = mSpeechUnderstander.startUnderstanding(mSpeechUnderstanderListener);
//                    if (ret != 0) {
//                    showTip("语义理解失败，错误码:" + ret);
//                    } else {
//                        showTip(getString(R.string.talk_begin));
//                    }
//                 }
//                 */
            }//onClick
        });//按钮的点击事件 btn
//        /*
//         * 将上传联系人功能添加到 onCreate 中
//        contact.setOnClickListener(new View.OnClickListener(){
//            @Override
//            public void onClick(View v)
//            {
//                ContactManager mgr = ContactManager.createManager(MainActivity.this, mContactListener);
//                mgr.asyncQueryAllContactsName();
//                showTip(getString(R.string.text_upload_contacts));
//            }
//        });//按钮的点击事件 contact
//        */
    }//onCreate

    /**
     * 初始化监听器（语音到语义）。
     */
    private InitListener mSpeechUdrInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "speechUnderstanderListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码："+code);
            }
        }
    };
    /**
     * 初始化监听器。(SpeechRecognizer, RecognizerDialog)
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败，错误码：" + code);
            }
        }
    };

//    /*
//     * 语义理解回调。
//     * 没有 UI 的界面
//     * 当前程序 没有 使用这个回调, 用的是带 UI 的回调
//
//     private SpeechUnderstanderListener mSpeechUnderstanderListener = new SpeechUnderstanderListener()
//     {
//     @Override
//     public void onVolumeChanged(int volume, byte[] bytes) {
//     showTip("当前正在说话，音量大小：" + volume);
//     Log.d(TAG, bytes.length+"");
//     }
//
//     @Override
//     public void onBeginOfSpeech() {
//     // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
//     showTip("开始说话");
//     }
//
//     @Override
//     public void onEndOfSpeech() {
//     // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
//     showTip("结束说话");
//     }
//
//     @Override
//     public void onResult(final UnderstanderResult understanderResult) {
//     if (null != understanderResult) {
//     Log.d(TAG, understanderResult.getResultString());
//
//     //显示
//     String text = understanderResult.getResultString();
//     if (!TextUtils.isEmpty(text)) {
//     mUnderstanderText.setText(text);
//     //分析 json 数据
//     parseJSONObject(text);
//     } else {
//     showTip("识别不正确");
//     }
//     }
//     }
//
//     @Override
//     public void onError(SpeechError speechError) {
//     showTip(speechError.getPlainDescription(true));
//     }
//
//     @Override
//     public void onEvent(int i, int i1, int i2, Bundle bundle) {
//
//     }
//     };
//     */
    /**
     * 听写UI监听器
     * 当前程序使用的是这个回调!
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {
            if (null != results) {
                Log.d(TAG, results.getResultString());

                //显示
                String text = results.getResultString();
                if (!TextUtils.isEmpty(text)) {
                    //mUnderstanderText.setText(text);  //强行显示 JSON 数据
                    //分析 json 数据
                    parseJSONObject(text);
                } else {
                    showTip("识别不正确");
                }
            }
        }
        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            showTip(error.getPlainDescription(true));
        }

    };

    /**
     * 获取联系人监听器。
     */
    private ContactManager.ContactListener mContactListener = new ContactManager.ContactListener() {
        @Override
        public void onContactQueryFinish(final String contactInfos, boolean b) {
            // 注：实际应用中除第一次上传之外，之后应该通过changeFlag判断是否需要上传，否则会造成不必要的流量.
            // 每当联系人发生变化，该接口都将会被回调，可通过ContactManager.destroy()销毁对象，解除回调。
            // if(changeFlag) {
            // 指定引擎类型
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //mUnderstanderText.setText(contactInfos);
                }
            });
            mSpeechUnderstander.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mSpeechUnderstander.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            int ret = mIat.updateLexicon("contact", contactInfos, mLexiconListener);
            if (ret != ErrorCode.SUCCESS) {
                showTip("联系人上传失败，错误码为:" + ret);
            }
        }
    };
    /**
     * 上传联系人/词表监听器。
     */
    private LexiconListener mLexiconListener = new LexiconListener() {
        @Override
        public void onLexiconUpdated(String lexiconId, SpeechError speechError) {
            if (speechError != null) {
                showTip(speechError.toString());
            } else {
                showTip(getString(R.string.text_upload_success));
            }
        }
    };


    /**
     * 对云端返回的 JSON 类型结果进行分析，提取关键信息
     * jsonObject 的解析
     */
    private void parseJSONObject(String json)
    {
        String strService = "";     //对应服务种类, 重要!
        String text = "";           //人 所说的话   SENT
        String textstr;             //机器 返回的话 RECEIVED
        JSONObject jsonObject = new JSONObject();
        Msg ret;

        try {
            jsonObject = new JSONObject(json);
            strService = jsonObject.optString("service");
            text = jsonObject.optString("text");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        ret = new Msg(text, Msg.TYPE_SENT);
        addToMsgListUI(ret, true);

        if (text.equals("开发者信息。") || text.equals("开发者。") || text.equals("开发人员。")) {
            textstr = "语音技术由科大讯飞提供\n" + "开发人员：余润身";
            ret = new Msg(textstr, Msg.TYPE_RECEIVED);
            msgList.add(ret);
            // 当有新消息时，刷新 ListView 中的显示
            msgAdapter.notifyItemInserted(msgList.size() - 1);
            // 将 ListView 定位到最后一行
            msgRecyclerView.scrollToPosition(msgList.size() - 1);
        }

        switch (strService) {
            case "telephone": //知道需求是 打电话!
                //电话打给谁, EG:打给张三
                String callName = jsonObject.optJSONObject("semantic")
                        .optJSONObject("slots").optString("name");
                //电话号码, EG:打给120
                String phoneCode = jsonObject.optJSONObject("semantic")
                        .optJSONObject("slots").optString("code");
                String phoneNumber;

                if ("".equals(phoneCode)) {  //报的是 人名, 不是 数字
                    //与手机通讯录里的人名进行比较!!!
                    //mUnderstanderText.append("\n\n" + callName);  //test, 看看是否识别 callName
                    phoneNumber = numberPhone(callName);
                    if ("".equals(phoneNumber)) {
                        textstr = "抱歉！联系人里没有" + callName + "这个人";
                        //mUnderstanderText.append("\n" + textstr);
                        //添加到消息列表中
                        ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                        addToMsgListUI(ret, true);
                    } else {
                        textstr = "好的，正在打电话给" + callName;
                        //mUnderstanderText.append("\n" + textstr);
                        ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                        addToMsgListUI(ret, true);
                        //调用 打电话 界面, 延时 2 秒
                        callSystemPhone(phoneNumber);
                    }
                } else { //报的是 数字, 不是 人名
                    textstr = "好的，正在打电话给" + phoneCode;
                    ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                    addToMsgListUI(ret, true);
                    //调用 打电话 界面, 延时 2 秒
                    callSystemPhone(phoneCode);
                }
                break;//case "telephone"
            case "app": //知道需求是 应用, 有打开(LAUNCH), 下载(DOWNLOAD), 卸载(UNINSTALL), 退出(EXIT)等等
                String strOperation = jsonObject.optString("operation");
                String appName = jsonObject.optJSONObject("semantic")
                        .optJSONObject("slots").optString("name");
                switch (strOperation) {
                    case "LAUNCH":  //打开(LAUNCH)
                        String result = checkAppExist(appName);
                        if ("NoSuchApp".equals(result)) {
                            textstr = "抱歉，手机里没有" + appName + "这个应用";
                            ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                            addToMsgListUI(ret, true);
                        } else { //手机里有这个软件
                            textstr = "正在打开" + appName;
                            ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                            addToMsgListUI(ret, true);
                            launchApp(result);
                        }
                        break;
                    case "EXIT": //退出(EXIT)
                        String exit_result = checkAppExist(appName);
                        if ("NoSuchApp".equals(exit_result)) {
                            textstr = "手机里没有" + appName + "这个应用，还叫我关闭???";
                            ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                            addToMsgListUI(ret, true);
                        } else { //手机里有这个软件
                            textstr = "功能略显鸡肋，没有实现，请到后台自行退出" + appName;
                            ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                            addToMsgListUI(ret, true);
                        }
                }//end inside switch
                break;//case "app"
            case "message":
                String content = jsonObject.optJSONObject("semantic")
                        .optJSONObject("slots").optString("content");
                String code = jsonObject.optJSONObject("semantic")
                        .optJSONObject("slots").optString("code");
                String sendName = jsonObject.optJSONObject("semantic")
                        .optJSONObject("slots").optString("name");
                if ("".equals(code)) {
                    //先查看联系人里面有没有这个人
                    //mUnderstanderText.append("\n\n" + sendName);  //test, 看看是否识别 callName
                    phoneNumber = numberSms(sendName);
                    if ("".equals(phoneNumber)) {
                        textstr = "抱歉！联系人里没有" + sendName + "这个人";
                        //mUnderstanderText.append("\n" + textstr);
                        //添加到消息列表中
                        ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                        addToMsgListUI(ret, true);
                    } else {
                        textstr = "好的，正在发短信给" + sendName;
                        //mUnderstanderText.append("\n" + textstr);
                        ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                        addToMsgListUI(ret, true);
                        //调用 发短信 界面, 延时 2 秒
                        sendMessage(phoneNumber, content);
                    }
                } else { //报的是 数字
                    textstr = "好的，正在发短信";
                    //mUnderstanderText.append("\n" + textstr);
                    ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                    addToMsgListUI(ret, true);
                    //调用 发短信 界面, 延时 2 秒
                    sendMessage(code, content);
                }
                break;
            case "weather":
                textstr = "正在查询";
                ret = new Msg(textstr, Msg.TYPE_RECEIVED);
                addToMsgListUI(ret, true);
                String webUri = jsonObject.optJSONObject("webPage").optString("url");
                //调用 浏览器 界面, 延时 2 秒
                openWebPage(webUri);

                break;

            //智能问答
            case "faq":
                String answer = jsonObject.optJSONObject("answer").optString("text");
                ret = new Msg(answer, Msg.TYPE_RECEIVED);
                if (answer.length() <= 15) {
                    addToMsgListUI(ret, true);
                } else {
                    addToMsgListUI(ret, false);
                }
                break;
            case "baike":
                answer = jsonObject.optJSONObject("answer").optString("text");
                ret = new Msg(answer, Msg.TYPE_RECEIVED);
                if (answer.length() <= 15) {
                    addToMsgListUI(ret, true);
                } else {
                    addToMsgListUI(ret, false);
                }
                break;
            case "chat":
                answer = jsonObject.optJSONObject("answer").optString("text");
                ret = new Msg(answer, Msg.TYPE_RECEIVED);
                if (answer.length() <= 15) {
                    addToMsgListUI(ret, true);
                } else {
                    addToMsgListUI(ret, false);
                }
                break;
            case "openQA":
                answer = jsonObject.optJSONObject("answer").optString("text");
                ret = new Msg(answer, Msg.TYPE_RECEIVED);
                if (answer.length() <= 10) {
                    addToMsgListUI(ret, true);
                } else {
                    addToMsgListUI(ret, false);
                }
                break;
            case "datetime":
                answer = jsonObject.optJSONObject("answer").optString("text");
                ret = new Msg(answer, Msg.TYPE_RECEIVED);
                if (answer.length() <= 10) {
                    addToMsgListUI(ret, true);
                } else {
                    addToMsgListUI(ret, false);
                }
                break;
            case "calc":
                answer = jsonObject.optJSONObject("answer").optString("text");
                ret = new Msg(answer, Msg.TYPE_RECEIVED);
                if (answer.length() <= 10) {
                    addToMsgListUI(ret, true);
                } else {
                    addToMsgListUI(ret, false);
                }
                break;
            default:
                ret = new Msg("抱歉，我没听懂你说什么", Msg.TYPE_RECEIVED);
                addToMsgListUI(ret, true);
                break;
        }//switch
    }//parseJSONObject

    /**
     *  通过输入的姓名, 获取电话号码
     *  用于电话查询，先在通话记录里面查询，如果不行再在所有联系人里面查询
     */
    public String numberPhone(String name)
    {
        String phoneNumber = "";
        //使用ContentResolver查找联系人数据
//        Cursor cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
//                null, null, null, null);
//        if (cursor != null) {
//            //遍历查询结果，找到所需号码
//            while (cursor.moveToNext()) {
//                //获取联系人ID
//                String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
//                //获取联系人的名字
//                String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
//                if (name.equals(contactName)) {
//                    //使用ContentResolver查找联系人的电话号码
//                    Cursor phone = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                            null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId, null, null);
//                    if (phone != null) {
//                        if (phone.moveToNext()) {
//                            phoneNumber = phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
//                            phone.close();
//                            return phoneNumber;
//                        }
//                    }
//                }
//            }//while
//        }
//        if (cursor != null) {
//            cursor.close();
//        }

        MainActivityPermissionsDispatcher.getCallLogCursorWithCheck(MainActivity.this, name);

//        if ("".equals(phoneNumberG)) {  //这个联系人之前从没通过电话，再在总的联系人里面找
//            Cursor callCurosr = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
//                null, null, null, null);
//            if (callCurosr != null) {
//                //遍历查询结果，找到所需号码
//                while (callCurosr.moveToNext()) {
//                    //获取联系人ID
//                    String contactId = callCurosr.getString(callCurosr.getColumnIndex(ContactsContract.Contacts._ID));
//                    //获取联系人的名字
//                    String contactName = callCurosr.getString(callCurosr.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
//                    if (name.equals(contactName)) {
//                        //使用ContentResolver查找联系人的电话号码
//                        Cursor phone = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                                null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId, null, null);
//                        if (phone != null) {
//                            if (phone.moveToNext()) { //肯定可以
//                                phoneNumber = phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
//                                phone.close();
//                                return phoneNumber;
//                            }
//                        }
//                        break;
//                    }
//                }//while
//            }
//        } else {
        phoneNumber = phoneNumberG;
        return phoneNumber;
    }
    /**
     *  通过输入的姓名, 获取电话号码，用于短信业务，直接从所有联系人里面查询姓名
     */
    public String numberSms(String name)
    {
        String phoneNumber = "";
        //使用ContentResolver查找联系人数据
        Cursor cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);
        if (cursor != null) {
            //遍历查询结果，找到所需号码
            while (cursor.moveToNext()) {
                //获取联系人ID
                String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                //获取联系人的名字
                String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                if (name.equals(contactName)) {
                    //使用ContentResolver查找联系人的电话号码
                    Cursor phone = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = " + contactId, null, null);
                    if (phone != null) {
                        if (phone.moveToNext()) {
                            phoneNumber = phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            phone.close();
                            return phoneNumber;
                        }
                    }
                }
            }//while
        }
        if (cursor != null) {
            cursor.close();
        }
        return phoneNumber;
    }
    /**
     * 调用系统自带的 打电话 软件,   延迟 2 秒实现
     */
    private void callSystemPhone(final String telephoneNumber)
    {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Intent intent = new Intent();
                intent.setAction("android.intent.action.DIAL");//CALL, 直接拨打电话, DIAL 是跳转到拨打界面
                intent.setData(Uri.parse("tel:" + telephoneNumber));
                startActivity(intent);
            }
        }.start();
    }
    /**
     *  调用系统自带的 发短信 软件,   延迟 2 秒实现
     */
    private void sendMessage(final String telephoneNumber, final String content)
    {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Uri smsToUri = Uri.parse("smsto:" + telephoneNumber);
                Intent intent = new Intent(Intent.ACTION_SENDTO, smsToUri);
                intent.putExtra("sms_body", content);
                startActivity(intent);
            }
        }.start();
    }
    /**
     *  调用系统自带的 浏览器 软件,   延迟 2 秒实现
     */
    private void openWebPage(final String webUrl)
    {
        //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
        //startActivity(intent);

        new Handler().postDelayed(new Runnable(){
            public void run() {
                //execute the task
                //网页显示
                LinearLayout main = (LinearLayout)findViewById(R.id.root);
                ViewGroup.LayoutParams lp = main.getLayoutParams();
                main.setOrientation(LinearLayout.HORIZONTAL);
                lp.width = MATCH_PARENT;
                lp.height = WRAP_CONTENT;
                main.setLayoutParams(lp);

                WebView webView = new WebView(MainActivity.this);
                main.addView(webView);

                WebSettings webSettings = webView.getSettings();
                webSettings.setJavaScriptEnabled(true);
                webSettings.setUseWideViewPort(true);
                webSettings.setLoadWithOverviewMode(true);

                webView.setWebViewClient(new WebViewClient());
                webView.loadUrl(webUrl);
            }
        }, 3000);

    }
    /**
     * 通过获得的 appName 来打开手机上的软件,
     * 返回 "NoSuchApp",   说明手机上 有 对应的软件
     * 返回 packageName, 说明手机上 有 对应的软件, 并且返回 packageName
     */
    private String checkAppExist(String appName)
    {
        String applicationName;
        PackageManager packageManager = MainActivity.this.getPackageManager();
        if (appName.equals("照相机") || appName.equals("摄像机")) {
            appName = "相机";
        }
        //获取手机内所有应用
        List<PackageInfo> packageInfoList = packageManager.getInstalledPackages(0);
        for (int i = 0; i < packageInfoList.size(); i++) {
            applicationName = packageManager.getApplicationLabel(packageInfoList.get(i).applicationInfo).toString();
            if (applicationName.equals(appName)) { //说明手机上有这个软件
                return packageInfoList.get(i).applicationInfo.packageName;
            }
        }
        return "NoSuchApp";
    }
    /**
     * 打开 软件,   延迟 2 秒实现
     */
    private void launchApp(final String packageName)
    {
        new Thread() {
            @Override
            public void run() {
                super.run();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                //打开相应软件
                Intent intent;
                PackageManager packageManager = MainActivity.this.getPackageManager();
                intent = packageManager.getLaunchIntentForPackage(packageName);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                MainActivity.this.startActivity(intent);
            }
        }.start();
    }

    /**
     * 初始化消息列表, 就是添加欢迎界面
     */
    private void initMsgs()
    {
        Msg welcome = new Msg(getString(R.string.welcome_word), Msg.TYPE_RECEIVED);
        msgList.add(welcome);
    }
    /**
     * 新消息展示在 RecyclerView 中, 并结合 语音合成
     */
    private void addToMsgListUI(Msg msg, boolean speak)
    {
        msgList.add(msg);
        // 当有新消息时，刷新 ListView 中的显示
        msgAdapter.notifyItemInserted(msgList.size() - 1);
        // 将 ListView 定位到最后一行
        msgRecyclerView.scrollToPosition(msgList.size() - 1);
        if (msg.getType() == Msg.TYPE_RECEIVED && speak) { //调用语音合成
            setmTts();
            mTts.startSpeaking(msg.getContent(), mSynListener);
        }
    }

    /**
     *  设置语音合成的参数
     */
    private void setmTts()
    {
        mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan");
        mTts.setParameter(SpeechConstant.SPEED, "50");   //设置语速
        mTts.setParameter(SpeechConstant.VOLUME, "80");  //设置音量
        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
    }
    /**
     *  设置语音合成的 合成 监听器
     */
    private SynthesizerListener mSynListener = new SynthesizerListener() {
        @Override
        public void onSpeakBegin() {

        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {

        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakResumed() {

        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {

        }

        @Override
        public void onCompleted(SpeechError speechError) {

        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    };

    /**
     *  屏幕翻转时 保存 msgList 中的数据
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState)
    {
        super.onSaveInstanceState(savedInstanceState);
        Log.i(TAG, "onSaveInstanceState");
        savedInstanceState.putSerializable(KEY_INDEX, (Serializable) msgList);
        savedInstanceState.putBoolean(KEY_BOOLEAN, isUploadContacts);
    }


    protected void onDestroy() {
        super.onDestroy();

        if( null != mSpeechUnderstander ){
            // 退出时释放连接
            mSpeechUnderstander.cancel();
            mSpeechUnderstander.destroy();
        }
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }



    @NeedsPermission(Manifest.permission.READ_CALL_LOG)
    void getCallLogCursor(String name) {
        phoneNumberG = "";
        Cursor callLogCursor = getContentResolver().query(callLogUri, project,
                selection, selectionArgs, sortOrder);
        if (callLogCursor != null) {
            while (callLogCursor.moveToNext()) {
                String contactsName = callLogCursor.getString(callLogCursor.getColumnIndexOrThrow("name"));
                if (name.equals(contactsName)) {
                    phoneNumberG = callLogCursor.getString(callLogCursor.getColumnIndexOrThrow("number"));
                    callLogCursor.close();
                }
            }//while
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }


}
