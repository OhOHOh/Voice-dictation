package com.yu.voiceassistanttest;

/**
 * Created by Yu on 2017/5/3.
 * 用于 UI 界面
 * 定义 Msg 类, 包含2个字段!
 */

public class Msg
{
    public static final int TYPE_RECEIVED = 0;  //表示这是一条收到的消息
    public static final int TYPE_SENT = 1;      //表示这是一条发出的消息

    //2 个字段 content--消息的内容, type--消息的类型
    private String content;
    private int type;

    public Msg(String content, int type)
    {
        this.content = content;
        this.type = type;
    }
    public String getContent()
    {
        return this.content;
    }
    public int getType()
    {
        return this.type;
    }
}
