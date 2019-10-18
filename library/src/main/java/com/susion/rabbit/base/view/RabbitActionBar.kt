package com.susion.rabbit.base.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.susion.rabbit.R
import com.susion.rabbit.utils.dp2px
import com.susion.rabbit.utils.getDrawable
import com.susion.rabbit.utils.throttleFirstClick
import io.reactivex.functions.Consumer
import kotlinx.android.synthetic.main.dev_tools_tool_bar.view.*

/**
 * susionwang at 2019-09-25
 */
class RabbitActionBar : RelativeLayout {

    var actionListener: ActionListener? = null

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.dev_tools_tool_bar, this)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp2px(45f))
        background = getDrawable(context, R.color.devtools_material_promary)
        mDevToolsToolsBarIvBack.throttleFirstClick(Consumer {
            actionListener?.onBackClick()
        })
    }

    fun setTitle(title: String) {
        mDevToolsToolsBarTvTitle.text = title
    }

    interface ActionListener {
        fun onBackClick()
    }

}