package com.tencent.qqmusic.api.demo.pcm

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioTrack.*
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue


class AudioTrackManager {
    companion object {
        private const val TAG = "AudioTrackManager"
        private const val LOG_LOOP = false

        //音频流类型
        private const val mStreamType = AudioManager.STREAM_MUSIC

        //指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
        private const val mSampleRateInHz = 44100

        //指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
        private const val mChannelConfig = AudioFormat.CHANNEL_IN_STEREO //STEREO立体声

        //指定音频量化位数 ,在AudioFormaat类中指定了以下各种可能的常量。通常我们选择ENCODING_PCM_16BIT和ENCODING_PCM_8BIT PCM代表的是脉冲编码调制，它实际上是原始音频样本。
        //因此可以设置每个样本的分辨率为16位或者8位，16位将占用更多的空间和处理能力,表示的音频也更加接近真实。
        private const val mAudioFormat = AudioFormat.ENCODING_PCM_16BIT

        //STREAM的意思是由用户在应用程序通过write方式把数据一次一次得写到audiotrack中。这个和我们在socket中发送数据一样，
        // 应用层从某个地方获取数据，例如通过编解码得到PCM数据，然后write到audiotrack。
        private const val mMode = AudioTrack.MODE_STREAM
    }

    private var mAudioTrack: AudioTrack? = null

    private var mInputStream: InputStream? = null //播放的数据流
    private var mPlayerThread: Thread? = null

    //指定缓冲区大小。调用AudioRecord类的getMinBufferSize方法可以获得。
    private var mMinBufferSize = 0

    var printMessageCallback: ((String) -> Unit)? = null
    var enterForeground: ((Boolean) -> Unit)? = null

    fun initData(sampleRateInHz: Int = mSampleRateInHz, channelConfig: Int = mChannelConfig, audioFormat: Int = mAudioFormat) {
        Log.d(TAG, "[initData】$sampleRateInHz, $channelConfig, $audioFormat")
        //根据采样率，采样精度，单双声道来得到frame的大小。
        mMinBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) //计算最小缓冲区
        //注意，按照数字音频的知识，这个算出来的是一秒钟buffer的大小。
        //创建AudioTrack
        mAudioTrack = AudioTrack(mStreamType, sampleRateInHz, channelConfig, audioFormat, mMinBufferSize, mMode)

        play()
    }

    /**
     * 销毁线程方法
     */
    private fun destroyThread() {
        try {
            if (null != mPlayerThread && Thread.State.RUNNABLE == mPlayerThread!!.state) {
                try {
                    Thread.sleep(500)
                    mPlayerThread!!.interrupt()
                } catch (e: Exception) {
                    mPlayerThread = null
                }
            }
            mPlayerThread = null
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mPlayerThread = null
        }
    }

    /**
     * 启动播放线程
     */
    private fun startThread() {
        destroyThread()
        if (mPlayerThread == null) {
            mPlayerThread = Thread(streamPlayerRunnable)
            mPlayerThread!!.start()
        }
    }

    private val streamPlayerRunnable = Runnable {
        try {
            //设置线程的优先级
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            Log.d(TAG, "streamPlayerRunnable")

            val tempBuffer = ByteArray(mMinBufferSize)
            var readCount = 0
            while (true) {
                if (LOG_LOOP) {
                    Log.d(TAG, "while true")
                }
                if (mInputStream!!.available() <= 0) {
                    if (LOG_LOOP) {
                        Log.d(TAG, "sleep")
                    }
                    printMessageCallback?.invoke("Play thread sleep \n PlayState:${mAudioTrack?.playState?.playStateToString()}")
                    Thread.sleep(500)
                    continue
                }

                readCount = mInputStream!!.read(tempBuffer)
                if (LOG_LOOP) {
                    Log.d(TAG, "readCount:$readCount")
                }
                if (readCount == AudioTrack.ERROR_INVALID_OPERATION || readCount == AudioTrack.ERROR_BAD_VALUE) {
                    continue
                }
                if (readCount != 0 && readCount != -1) {
                    //判断AudioTrack未初始化，停止播放的时候释放了，状态就为STATE_UNINITIALIZED
                    if (mAudioTrack!!.state == AudioTrack.STATE_UNINITIALIZED) {
                        initData()
                    }

                    printMessageCallback?.invoke("AudioTrack write $readCount Bytes\n PlayState:${mAudioTrack?.playState?.playStateToString()}")
                    mAudioTrack!!.write(tempBuffer, 0, readCount)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun Int.playStateToString(): String {
        return when (this) {
            PLAYSTATE_STOPPED -> {
                "PLAYSTATE_STOPPED"
            }
            PLAYSTATE_PAUSED -> {
                "PLAYSTATE_PAUSED"
            }
            PLAYSTATE_PLAYING -> {
                "PLAYSTATE_PLAYING"
            }
            else -> {
                "unknown"
            }
        }
    }

    /**
     * 播放文件
     *
     * @param path
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun setPath(path: String) {
        val file = File(path)
        mInputStream = DataInputStream(FileInputStream(file))
    }

    /**
     * 启动播放
     *
     * @param path
     */
    fun startPlay(path: String) {
        try {
            //AudioTrack未初始化
            setPath(path)
            startThread()
        } catch (e: Exception) {
            Log.d(TAG, "startPlay: Exception$e")
        }
    }

    fun startPlay(inputStream: InputStream?) {
        initData()
        try {
            mInputStream = DataInputStream(inputStream)
            startThread()
        } catch (e: Exception) {
            Log.d(TAG, "startPlay: Exception$e")
        }
    }

    fun startPlayByFileDescriptor(fileDescriptor: ParcelFileDescriptor) {
        mInputStream = ParcelFileDescriptor.AutoCloseInputStream(fileDescriptor)
        startThread()
    }

    /**
     * 停止播放
     */
    fun stopPlay() {
        try {
            destroyThread() //销毁线程
            if (mAudioTrack != null) {
                if (mAudioTrack!!.state == AudioRecord.STATE_INITIALIZED) { //初始化成功
                    mAudioTrack!!.stop() //停止播放
                }
                if (mAudioTrack != null) {
                    mAudioTrack!!.release() //释放audioTrack资源
                }
                mAudioTrack = null
            }
            if (mInputStream != null) {
                mInputStream!!.close() //关闭数据输入流
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        enterForeground?.invoke(false)
    }


    fun pause() {
        mAudioTrack?.pause()
        enterForeground?.invoke(false)
    }

    fun play() {
        mAudioTrack?.play()
        enterForeground?.invoke(true)
    }

}