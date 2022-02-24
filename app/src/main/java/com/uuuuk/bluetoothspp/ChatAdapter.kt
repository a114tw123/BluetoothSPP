package com.uuuuk.bluetoothspp

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private var itemList: ArrayList<ChatModel>): RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    private lateinit var mContext: Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false)
        mContext = view.context
        return ViewHolder(view as LinearLayout,viewType)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
    override fun getItemViewType(position: Int): Int {
        if (itemList[position].src){
            return 1
        }else{
            return 2
        }
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when(holder.mViewType){
            1->{
                holder.tv_msg.setBackgroundColor(mContext.getColor(android.R.color.darker_gray))
                holder.linear_chat_item.gravity=Gravity.END
            }
            else->{
                holder.tv_msg.setBackgroundColor(mContext.getColor(R.color.colorPrimary))
            }
        }
        holder.tv_msg.text=itemList[position].msg
    }
    class ViewHolder(view: LinearLayout,viewType: Int) : RecyclerView.ViewHolder(view){
        val mViewType=viewType
        val linear_chat_item=view.findViewById<LinearLayout>(R.id.linear_chat_item)
        val tv_msg=view.findViewById<TextView>(R.id.tv_msg)

    }
}