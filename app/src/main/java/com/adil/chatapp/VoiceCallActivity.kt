package com.adil.chatapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.adil.chatapp.databinding.ActivityVoiceCallBinding
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

class VoiceCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OTHER_UID = "other_uid"
        const val EXTRA_OTHER_NAME = "other_name"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_CHANNEL = "channel"
        const val EXTRA_CURRENT_UID = "current_uid"
        
        private const val CALL_STATE_CONNECTING = 0
        private const val CALL_STATE_ACTIVE = 1
        private const val CALL_STATE_ENDED = 2
    }

    private lateinit var binding: ActivityVoiceCallBinding
    private var mRtcEngine: RtcEngine? = null
    private var mCallState = CALL_STATE_CONNECTING
    private var mStartTime = 0L
    private val mHandler = Handler(Looper.getMainLooper())
    private var currentUid = 0
    private var otherUid = 0
    private var otherName = ""
    private var appId = ""
    private var token = ""
    private var channelName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras from intent
        otherUid = intent.getIntExtra(EXTRA_OTHER_UID, 0)
        otherName = intent.getStringExtra(EXTRA_OTHER_NAME) ?: ""
        appId = intent.getStringExtra(EXTRA_APP_ID) ?: ""
        token = intent.getStringExtra(EXTRA_TOKEN) ?: ""
        channelName = intent.getStringExtra(EXTRA_CHANNEL) ?: ""
        currentUid = intent.getIntExtra(EXTRA_CURRENT_UID, 0)

        binding.tvCallerName.text = otherName
        binding.btnHangup.setOnClickListener { endCall() }

        initializeAgoraRTC()
    }

    private fun initializeAgoraRTC() {
        try {
            if (appId.isEmpty()) {
                Toast.makeText(this, "App ID غير صحيح", Toast.LENGTH_SHORT).show()
                return
            }

            val config = RtcEngineConfig()
            config.mContext = this
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    mCallState = CALL_STATE_ACTIVE
                    mStartTime = System.currentTimeMillis()
                    mHandler.post { updateCallDuration() }
                    runOnUiThread {
                        binding.tvCallStatus.text = "المكالمة نشطة"
                        binding.btnHangup.isEnabled = true
                    }
                }

                override fun onUserJoined(uid: Int, elapsed: Int) {
                    runOnUiThread {
                        binding.tvCallStatus.text = "متصل برقم: $uid"
                    }
                }

                override fun onUserOffline(uid: Int, reason: Int) {
                    runOnUiThread {
                        binding.tvCallStatus.text = "المستخدم قطع الاتصال"
                        mHandler.postDelayed({ endCall() }, 2000)
                    }
                }

                override fun onError(err: Int) {
                    val errorMsg = when (err) {
                        Constants.ERR_INVALID_APP_ID -> "App ID غير صحيح"
                        Constants.ERR_TOKEN_EXPIRED -> "الرمز انتهت صلاحيته"
                        Constants.ERR_INVALID_CHANNEL_NAME -> "اسم القناة غير صحيح"
                        else -> "خطأ: $err"
                    }
                    runOnUiThread {
                        binding.tvCallStatus.text = errorMsg
                        Toast.makeText(this@VoiceCallActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            mRtcEngine = RtcEngine.create(config)
            mRtcEngine?.enableAudio()

            // Join channel
            joinChannel()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تهيئة Agora: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun joinChannel() {
        try {
            val options = ChannelMediaOptions()
            options.autoSubscribeAudio = true
            options.publishMicrophoneTrack = true
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER

            mRtcEngine?.joinChannel(token, channelName, currentUid, options)
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في الانضمام للقناة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCallDuration() {
        if (mCallState != CALL_STATE_ACTIVE) return

        val elapsedSeconds = (System.currentTimeMillis() - mStartTime) / 1000
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        val timeString = String.format("%02d:%02d", minutes, seconds)

        binding.tvCallDuration.text = timeString
        mHandler.postDelayed({ updateCallDuration() }, 1000)
    }

    private fun endCall() {
        try {
            mCallState = CALL_STATE_ENDED
            mRtcEngine?.leaveChannel()
            mRtcEngine?.destroy()
            mHandler.removeCallbacksAndMessages(null)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في إنهاء المكالمة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mCallState != CALL_STATE_ENDED) {
            endCall()
        }
    }
}
