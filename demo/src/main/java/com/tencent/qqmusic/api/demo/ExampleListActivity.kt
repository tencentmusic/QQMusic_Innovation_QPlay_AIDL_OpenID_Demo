package com.tencent.qqmusic.api.demo

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import com.tencent.qqmusic.api.demo.pcm.PcmExampleActivity
import kotlinx.android.synthetic.main.activity_example_list.*

class ExampleListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example_list)

        val exampleList = listOf(
                Example("QPlayAidl基本功能及接口测试") {
                    startActivity(Intent(this, VisualActivity::class.java))
                },
                Example("QQ音乐登录demo") {
                    startActivity(Intent(this, LoginExampleActivity::class.java))
                },
                Example("Pcm传输demo") {
                    startActivity(Intent(this, PcmExampleActivity::class.java))
                }
        )
        lv_examples.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, exampleList)
        lv_examples.setOnItemClickListener { _, _, position, _ ->
            exampleList[position].action()
        }
    }
}

class Example(
        private val title: String,
        val action: () -> Unit
) {
    override fun toString(): String {
        return title
    }
}