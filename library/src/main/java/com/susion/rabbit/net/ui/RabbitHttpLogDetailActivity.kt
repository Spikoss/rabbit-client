package com.susion.rabbit.net.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.susion.rabbit.R
import com.susion.rabbit.base.RabbitBaseActivity
import com.susion.rabbit.net.entities.RabbitHttpLogInfo
import com.susion.rabbit.utils.showToast
import kotlinx.android.synthetic.main.activity_http_log_detail.*
import java.text.SimpleDateFormat
import java.util.*

class RabbitHttpLogDetailActivity : RabbitBaseActivity() {

    companion object{
        private val LOG_INFO = "log_info"
        fun start(context: Context, logInfo: RabbitHttpLogInfo){
            val intent = Intent(context, RabbitHttpLogDetailActivity::class.java)
            intent.putExtra(LOG_INFO, logInfo)
            context.startActivity(intent)
        }
    }

    private val logInfo by lazy {
        intent.getSerializableExtra(LOG_INFO) as? RabbitHttpLogInfo
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http_log_detail)
        setBackListener(mHttpLogDetailActionBar)
        mHttpLogDetailActionBar.setTitle("日志详情")

        if (logInfo == null || logInfo?.isValid() == false){
            showToast(this, "log日志文件已损坏!")
            return
        }

        mHttpLogDetailTvRequestPath.text = "${logInfo?.path?:""}   ${getTime(logInfo?.time?:0L)}"
        if (logInfo!!.requestParams.isNotEmpty()){
            mHttpLogDetailTvRequestParams.visibility = View.VISIBLE
            mHttpLogDetailTvRequestParams.text = logInfo?.requestParams?.toString()
        }else{
            mHttpLogDetailTvRequestParams.visibility = View.GONE
        }

        if (logInfo!!.requestBody.isNotEmpty()){
            mHttpLogDetailTvRequestBody.visibility = View.VISIBLE
            mHttpLogDetailTvRequestBody.text = logInfo?.requestBody
        }else{
            mHttpLogDetailTvRequestBody.visibility = View.GONE
        }

        if (logInfo!!.responseStr.isNotEmpty()){
            mHttpLogDetailJsonView.bindJson(logInfo!!.responseStr)
        }
    }

    private fun getTime(time:Long):String{
        return SimpleDateFormat("MM/dd HH:mm:ss").format(Date(time))
    }

}