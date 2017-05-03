package com.yu.voiceassistanttest;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Yu on 2017/5/3.
 * Msg 的适配器!
 */

public class MsgAdapter extends RecyclerView.Adapter<MsgAdapter.ViewHolder>
{
    private List<Msg> mMsgList;

    static class ViewHolder extends RecyclerView.ViewHolder
    {
        LinearLayout leftLayout;
        LinearLayout rightLayout;

        TextView leftMsg;
        TextView rightMsg;

        public ViewHolder(View view)
        {
            super(view);
            leftLayout = (LinearLayout) view.findViewById(R.id.left_layout);
            rightLayout = (LinearLayout) view.findViewById(R.id.right_layout);
            leftMsg = (TextView) view.findViewById(R.id.left_msg);
            rightMsg = (TextView) view.findViewById(R.id.right_msg);
        }
    }
    public MsgAdapter(List<Msg> msgList)
    {
        this.mMsgList = msgList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
    {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.msg_item,
                parent, false);
        return new ViewHolder(view);
    }
    @Override
    public void onBindViewHolder(ViewHolder holder, int position)
    {
        Msg msg = mMsgList.get(position);
        if (msg.getType() == Msg.TYPE_RECEIVED) //如果是收到的消息
        {
            holder.leftLayout.setVisibility(View.VISIBLE);
            holder.rightLayout.setVisibility(View.GONE);
            holder.leftMsg.setText(msg.getContent());
        }
        else if (msg.getType() == Msg.TYPE_SENT) //如果是发送的消息
        {
            holder.leftLayout.setVisibility(View.GONE);
            holder.rightLayout.setVisibility(View.VISIBLE);
            holder.rightMsg.setText(msg.getContent());
        }
    }
    @Override
    public int getItemCount()
    {
        return mMsgList.size();
    }
}

