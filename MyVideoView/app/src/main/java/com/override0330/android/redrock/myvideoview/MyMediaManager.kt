package com.override0330.android.redrock.myvideoview

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.*
import kotlin.concurrent.timerTask
import android.os.Handler

/**
 * 视频控制类
 */
class MyMediaManager{
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var activity: Activity
    private lateinit var surfaceView: SurfaceView

    private lateinit var videoTitle: TextView
    private lateinit var videoControl: ImageView
    private lateinit var videoSchedule: SeekBar
    private lateinit var videoFull: ImageView

    private lateinit var timer: Timer
    private lateinit var parentView: LinearLayout
    private var videoHeight: Int = 0
    lateinit var controlBar: View
    lateinit var detailBar: View

    private lateinit var onError:MediaPlayer.OnErrorListener

    /**
     * 建造者模式
     */
    private constructor(activity: Activity, surface: SurfaceView, textView: TextView, controlButton: ImageView, controlSeekBar: SeekBar, controlScreen: ImageView,parent: LinearLayout) {
        this.activity = activity
        this.surfaceView = surface
        this.videoTitle = textView
        this.videoControl = controlButton
        this.videoSchedule = controlSeekBar
        this.videoFull = controlScreen
        this.parentView = parent
    }

    class Builder {
        //必要参数
        private lateinit var activity: Activity
        private lateinit var surface: SurfaceView
        private lateinit var parentView: LinearLayout
        //非必要
        private lateinit var title: TextView
        private lateinit var controlButton: ImageView
        private lateinit var controlSeekBar: SeekBar
        private lateinit var controlScreen: ImageView

        constructor(activity: Activity, surface: SurfaceView, parent: LinearLayout) {
            this.activity = activity
            this.surface = surface
            this.parentView = parent
        }

        public fun setTitle(textView: TextView): Builder {
            this.title = textView
            return this
        }

        public fun setControlButton(imageView: ImageView): Builder {
            this.controlButton = imageView
            return this
        }

        public fun setControlSeekBar(seekBar: SeekBar): Builder {
            this.controlSeekBar = seekBar
            return this
        }

        public fun setControlScreen(imageView: ImageView): Builder {
            this.controlScreen = imageView
            return this
        }

        public fun build(): MyMediaManager {
            return MyMediaManager(activity, surface, title, controlButton, controlSeekBar, controlScreen,parentView)
        }
    }

    /**
     *初始化surfaceView，视频资源，seekbar，按钮监听
     */
    public fun init() {
        //初始化变量
        controlBar = videoSchedule.parent as View
        detailBar = videoTitle.parent as View
        mediaPlayer = MediaPlayer()

        //初始化SurfaceView
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            //大小改变的回调
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            }

            //销毁的回调
            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                //同时销毁 mediaPlayer
                mediaPlayer.release()
            }

            //初始化成功的回调
            override fun surfaceCreated(holder: SurfaceHolder?) {
                mediaPlayer.setDataSource(activity, Uri.parse("android.resource://" + activity.packageName + "/raw/a"));
                mediaPlayer.isLooping = true
                mediaPlayer.setDisplay(surfaceView.holder)
                mediaPlayer.setScreenOnWhilePlaying(true)
                mediaPlayer.prepare()
                videoHeight = surfaceView.height
            }
        })

        //播放器准备完毕的回调
        mediaPlayer.setOnPreparedListener {
            //开始播放
            mediaPlayer.start()
            //适配分辨率
            fitScreen()
            //倒计时隐藏bar
            startToHidden()
            videoSchedule.max = mediaPlayer.duration
            //开启seekBar更新线程
            startRefreshSeekBar()

        }
        //播放器播放完毕的回调
        mediaPlayer.setOnCompletionListener {
            mediaPlayer.reset()
            mediaPlayer.pause()
        }
        //播放错误回调,通过注入来实现具体逻辑
        mediaPlayer.setOnErrorListener(onError)

        /**
         * 滑动条视频跳转的监听
         */
        videoSchedule.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var isTouch = false
            //正在脱移
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (isTouch) {
                    val position = seekBar.progress
                    mediaPlayer.seekTo(position)
                    mediaPlayer.pause()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isTouch = false
                mediaPlayer.start()
            }
        })
        /**
         * 暂停播放按钮的监听
         */
        videoControl.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                activity.runOnUiThread { videoControl.setImageResource(R.drawable.play) }
            } else {
                mediaPlayer.start()
                activity.runOnUiThread { videoControl.setImageResource(R.drawable.pause) }
            }
        }
        /**
         * 全屏按钮的监听
         */
        videoFull.setOnClickListener {
            val state: Int = activity.resources.configuration.orientation
            if (state == Configuration.ORIENTATION_LANDSCAPE){
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity.runOnUiThread { videoFull.setImageResource(R.drawable.fullscreen) }
            }else{
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                activity.runOnUiThread { videoFull.setImageResource(R.drawable.smallscreen) }
            }
        }
    }
    /**
     * SeekBar的进度更新
     */
    private fun startRefreshSeekBar() {
        timer = Timer()
        timer.schedule(timerTask {
            val nowPosition = mediaPlayer.currentPosition
            videoSchedule.progress = nowPosition
        }, 0, 10)
    }
    private fun fitScreen(){
        //获取父布局和surface布局的参数
        val parentParameters = parentView.layoutParams
        //计算视频的宽高
        val videoWidth: Float = mediaPlayer.videoWidth.toFloat()
        val videoHeight: Float = mediaPlayer.videoHeight.toFloat()
        //保持视频的高宽比
        val videoRatio: Float = (videoWidth/videoHeight)
        Log.d("视频的高宽比为","$videoRatio width: $videoWidth height: $videoHeight")
        //通过改变surface的父布局来改变,高度锁定，改变宽度来维持高宽比
        parentParameters.width = (videoWidth/videoHeight*parentParameters.height).toInt()
        parentView.layoutParams = parentParameters
    }
    //转换成全屏模式
    fun changeToFullScreen(){
        //因为已经对surfaceView做了保持比例适配，所以只需要改变父布局的高度就可以做到适配了
        val parentParameter = parentView.layoutParams
        parentParameter.height = activity.windowManager.defaultDisplay.height
        //隐藏状态栏
        this.activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        parentView.layoutParams = parentParameter
        fitScreen()
    }
    fun changeToSmallScreen(){
        val parentParameter = parentView.layoutParams
        parentParameter.height = videoHeight
        //显示状态栏
        this.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        parentView.layoutParams = parentParameter
        fitScreen()
    }

    //启动计时器，3秒后隐藏控制条和标题
    private fun startToHidden(){
        Handler().run {
            postDelayed(Runnable {
                //通过子view获取父容器
                controlBar.visibility = View.GONE
                detailBar.visibility = View.GONE
            }, 3000)
        }
    }
    //直接显示控制条和标题
    private fun showControlBar(){
        Handler().run {
            postDelayed(Runnable {
                controlBar.visibility = View.VISIBLE
                detailBar.visibility = View.VISIBLE
            }, 0)
        }
    }
}