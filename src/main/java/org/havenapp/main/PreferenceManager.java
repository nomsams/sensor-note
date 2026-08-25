/*
 * Copyright (c) 2017 Nathanial Freitas
 *
 *   This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.havenapp.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.havenapp.main.sensors.motion.LuminanceMotionDetector;
import org.havenapp.main.utils.CryptoUtils;

import java.io.File;
import java.util.Date;
import java.util.Objects;

public class PreferenceManager {

    public SharedPreferences appSharedPrefs;
    private Editor prefsEditor;

    public static final String LOW = \"Low\";
    public static final String MEDIUM = \"Medium\";
    public static final String HIGH = \"High\";
    public static final String OFF = \"Off\";

    public static final String FRONT = \"Front\";
    public static final String BACK = \"Back\";
    public static final String NONE = \"None\";

    private static final String APP_SHARED_PREFS=\"org.havenapp.main\";
    private static final String ACCELEROMETER_ACTIVE=\"accelerometer_active\";
    private static final String ACCELEROMETER_SENSITIVITY=\"accelerometer_sensibility\";
    private static final String CAMERA_ACTIVE=\"camera_active\";
    public static final String CAMERA=\"camera\";
    public static final String CAMERA_SENSITIVITY=\"camera_sensitivity\";
    public static final String CONFIG_MOVEMENT =\"config_movement\";
    public static final String HEARTBEAT_MONITOR_ACTIVE=\"heartbeat_monitor_active\";
    public static final String HEARTBEAT_MONITOR_DELAY=\"heartbeat_monitor_delay\";
    public static final String HEARTBEAT_MONITOR_MESSAGE=\"heartbeat_monitor_message\";
    public static final String MONITOR_SERVICE_ACTIVE=\"monitor_service_active\";
    private static final String FLASH_ACTIVE=\"flash_active\";
    private static final String MICROPHONE_ACTIVE=\"microphone_active\";
    private static final String MICROPHONE_SENSITIVITY=\"microphone_sensitivity\";
    public static final String CONFIG_SOUND = \"config_sound\";
    public static final String CONFIG_TIME_DELAY = \"config_delay_time\";
    public static final String REGISTER_SIGNAL = \"register_signal\";
    public static final String VERIFY_SIGNAL = \"verify_signal\";
    public static final String VOICE_VERIFY_SIGNAL = \"voice_verify_signal\";
    public static final String RESET_SIGNAL_CONFIG = \"reset_signal_config\";
    private static final String UNLOCK_CODE=\"unlock_code\";

    private static final String ACCESS_TOKEN=\"access_token\";
    private static final String DELEGATED_ACCESS_TOKEN=\"deferred_access_token\";

    private static final String PHONE_ID=\"phone_id\";
    private static final String TIMER_DELAY=\"timer_delay\";
    private static final String VIDEO_LENGTH=\"video_length\";
    public static final String CONFIG_VIDEO_LENGTH =\"config_video_length\";
    private static final String DIR_PATH = CONFIG_BASE_STORAGE_DEFAULT;

    public static final String REMOTE_ACCESS_ACTIVE = \"remote_access_active\";
    public static final String REMOTE_ACCESS_ONION = \"remote_access_onion\";
    public static final String REMOTE_ACCESS_CRED = \"remote_access_credential\";

    private static final String SIGNAL_USERNAME = \"signal_username\";
    private static final String SIGNAL_VERIFIED_USERNAME = \"signal_verified_username\";

    private static final String FIRST_LAUNCH = \"first_launch\";

    public static final String NOTIFICATION_TIME = \"notification_time\";

    public static final String DISABLE_BATTERY_OPT = \"config_battery_optimizations\";

    private static final String CURRENT_EVENT_START_TIME = \"current_event_start_time\";

    public static final String CONFIG_BASE_STORAGE = \"config_base_storage\";
    private static final String CONFIG_BASE_STORAGE_DEFAULT = \"/haven\";

    public static final String TELEGRAM_ENABLED = \"telegram_enabled\";
    public static final String TELEGRAM_BOT_TOKEN = \"telegram_bot_token\";
    public static final String TELEGRAM_CHAT_ID = \"telegram_chat_id\";
    public static final String AUDIO_SPECTROGRAM_ENABLED = \"audio_spectrogram_enabled\";
    private static final String EMF_SENSITIVITY = \"emf_sensitivity\";
    private static final String PRESSURE_SENSITIVITY = \"pressure_sensitivity\";
    private static final String DISARM_CODE = \"disarm_code\";
    private static final String PANIC_CODE = \"panic_code\";
    private static final String PENDING_ARM_REQUEST = \"pending_arm_request\";
    public static final String OPERATING_MODE = \"operating_mode\";
    public static final String SENSOR_ENABLED_PREFIX = \"sensor_enabled_\";

    // keeping the key value same for data migration.
    static final String REMOTE_PHONE_NUMBER = \"sms_number\";
    static final String REMOTE_NOTIFICATION_ACTIVE = \"remote_notification_active\";

    private Context context;

    public PreferenceManager(Context context) {
        this.context = context;
        this.appSharedPrefs = context.getSharedPreferences(APP_SHARED_PREFS, AppCompatActivity.MODE_PRIVATE);
        this.prefsEditor = appSharedPrefs.edit();
        prefsEditor.apply();
    }

    public boolean isFirstLaunch() {
        return appSharedPrefs.getBoolean(FIRST_LAUNCH, true);
    }

    public void setFirstLaunch(boolean firstLaunch) {
        prefsEditor.putBoolean(FIRST_LAUNCH, firstLaunch);
        prefsEditor.apply();
    }

    public String getSignalUsername () {
        return appSharedPrefs.getString(SIGNAL_USERNAME,null);
    }

    public void setSignalUsername (String signalUsername) {
        prefsEditor.putString(SIGNAL_USERNAME,signalUsername);
        prefsEditor.apply();
    }

    @Nullable
    public String getVerifiedSignalUsername() {
        return appSharedPrefs.getString(SIGNAL_VERIFIED_USERNAME, null);
    }

    public void setVerifiedSignalUsername(String verifiedSignalUsername) {
        prefsEditor.putString(SIGNAL_VERIFIED_USERNAME, verifiedSignalUsername);
        prefsEditor.apply();
    }

    public boolean isSignalVerified() {
        return !TextUtils.isEmpty(getSignalUsername()) &&
                getSignalUsername().equals(getVerifiedSignalUsername());
    }

    public void activateRemoteAccess (boolean active) {
        prefsEditor.putBoolean(REMOTE_ACCESS_ACTIVE,active);
        prefsEditor.apply();
    }

    public boolean getRemoteAccessActive () {
        return appSharedPrefs.getBoolean(REMOTE_ACCESS_ACTIVE,false);
    }

    public void activateMonitorService (boolean active) {
        prefsEditor.putBoolean(MONITOR_SERVICE_ACTIVE,active);
        prefsEditor.apply();
    }

    public boolean getMonitorServiceActive () {
        return appSharedPrefs.getBoolean(MONITOR_SERVICE_ACTIVE,false);
    }

    public void setRemoteAccessOnion (String onionAddress) {
        prefsEditor.putString(REMOTE_ACCESS_ONION,onionAddress);
        prefsEditor.apply();
    }

    public String getRemoteAccessOnion () {
        return appSharedPrefs.getString(REMOTE_ACCESS_ONION,\"\");
    }

    public void setRemoteAccessCredential (String remoteCredential) {
        prefsEditor.putString(REMOTE_ACCESS_CRED,remoteCredential);
        prefsEditor.apply();
    }

    public String getRemoteAccessCredential () {
        return appSharedPrefs.getString(REMOTE_ACCESS_CRED,null);
    }

    public void activateAccelerometer(boolean active) {
        prefsEditor.putBoolean(ACCELEROMETER_ACTIVE, active);
        prefsEditor.apply();
    }

    public boolean getAccelerometerActivation() {
        return appSharedPrefs.getBoolean(ACCELEROMETER_ACTIVE, true);
    }

    public void setAccelerometerSensitivity(String sensitivity) {
        prefsEditor.putString(ACCELEROMETER_SENSITIVITY, sensitivity);
        prefsEditor.apply();
    }

    public String getAccelerometerSensitivity() {
        return appSharedPrefs.getString(ACCELEROMETER_SENSITIVITY, HIGH);
    }

    public void setActivateVideoMonitoring(boolean active) {
        prefsEditor.putBoolean(context.getResources().getString(R.string.video_active_preference_key), active);
        prefsEditor.apply();
    }

    public boolean getVideoMonitoringActive() {
        return appSharedPrefs.getBoolean(context.getResources().getString(R.string.video_active_preference_key), false);
    }

    public void activateTelegram(boolean active) {
        prefsEditor.putBoolean(TELEGRAM_ENABLED, active);
        prefsEditor.apply();
    }

    public boolean getTelegramEnabled() {
        return appSharedPrefs.getBoolean(TELEGRAM_ENABLED, false);
    }

    public void setTelegramBotToken(String botToken) {
        CryptoUtils.putEncryptedString(context, TELEGRAM_BOT_TOKEN, botToken);
    }

    public String getTelegramBotToken() {
        return CryptoUtils.getEncryptedString(context, TELEGRAM_BOT_TOKEN, \"\");
    }

    public void setTelegramChatId(String chatId) {
        CryptoUtils.putEncryptedString(context, TELEGRAM_CHAT_ID, chatId);
    }

    public String getTelegramChatId() {
        return CryptoUtils.getEncryptedString(context, TELEGRAM_CHAT_ID, \"\");
    }

    public boolean isTelegramConfigured() {
        return !TextUtils.isEmpty(getTelegramBotToken()) && !TextUtils.isEmpty(getTelegramChatId());
    }

    public void setAudioSpectrogramEnabled(boolean enabled) {
        prefsEditor.putBoolean(AUDIO_SPECTROGRAM_ENABLED, enabled);
        prefsEditor.apply();
    }

    public boolean getAudioSpectrogramEnabled() {
        return appSharedPrefs.getBoolean(AUDIO_SPECTROGRAM_ENABLED, false);
    }

    public void setEmfSensitivity(String sensitivity) {
        prefsEditor.putString(EMF_SENSITIVITY, sensitivity);
        prefsEditor.apply();
    }

    public String getEmfSensitivity() {
        return appSharedPrefs.getString(EMF_SENSITIVITY, \"25\");
    }

    public void setPressureSensitivity(String sensitivity) {
        prefsEditor.putString(PRESSURE_SENSITIVITY, sensitivity);
        prefsEditor.apply();
    }

    public float getPressureSensitivity() {
        try {
            return Float.parseFloat(appSharedPrefs.getString(PRESSURE_SENSITIVITY, \"0.20\"));
        } catch (Exception ignored) {
            return 0.20f;
        }
    }

    public void setDisarmCode(String code) {
        // Store hash for verification, not plaintext
        String hash = CryptoUtils.hashString(code);
        CryptoUtils.putEncryptedString(context, DISARM_CODE + \"_hash\", hash);
    }

    public String getDisarmCode() {
        // Return empty - we only store hash for verification
        return \"\";
    }

    /**
     * Check if a disarm code has been configured
     */
    public boolean hasDisarmCode() {
        String storedHash = CryptoUtils.getEncryptedString(context, DISARM_CODE + \"_hash\", \"\");
        return !storedHash.isEmpty();
    }

    /**
     * Verify a disarm code against stored hash
     */
    public boolean verifyDisarmCode(String code) {
        String storedHash = CryptoUtils.getEncryptedString(context, DISARM_CODE + \"_hash\", \"\");
        if (storedHash.isEmpty()) return false;
        String inputHash = CryptoUtils.hashString(code);
        return CryptoUtils.constantTimeEquals(storedHash, inputHash);
    }

    public void setPanicCode(String code) {
        // Store hash for verification, not plaintext
        String hash = CryptoUtils.hashString(code);
        CryptoUtils.putEncryptedString(context, PANIC_CODE + \"_hash\", hash);
    }

    public String getPanicCode() {
        // Return empty - we only store hash for verification
        return \"\";
    }

    /**
     * Check if a panic code has been configured
     */
    public boolean hasPanicCode() {
        String storedHash = CryptoUtils.getEncryptedString(context, PANIC_CODE + \"_hash\", \"\");
        return !storedHash.isEmpty();
    }

    public boolean verifyPanicCode(String code) {
        String storedHash = CryptoUtils.getEncryptedString(context, PANIC_CODE + \"_hash\", \"\");
        if (storedHash.isEmpty()) return false;
        String inputHash = CryptoUtils.hashString(code);
        return CryptoUtils.constantTimeEquals(storedHash, inputHash);
    }

    public void setPendingArmRequest(boolean pending) {
        prefsEditor.putBoolean(PENDING_ARM_REQUEST, pending);
        prefsEditor.apply();
    }

    public boolean getPendingArmRequest() {
        return appSharedPrefs.getBoolean(PENDING_ARM_REQUEST, false);
    }

    public void setSensorEnabled(int sensorType, boolean enabled) {
        prefsEditor.putBoolean(SENSOR_ENABLED_PREFIX + sensorType, enabled);
        prefsEditor.apply();
    }

    public boolean isSensorEnabled(int sensorType, boolean defaultValue) {
        return appSharedPrefs.getBoolean(SENSOR_ENABLED_PREFIX + sensorType, defaultValue);
    }

    public void setSilentOperations(boolean silent) {
        prefsEditor.putBoolean(\"silent_operations\", silent);
        prefsEditor.apply();
    }

    public boolean getSilentOperations() {
        return appSharedPrefs.getBoolean(\"silent_operations\", true);
    }

    public void setOperatingMode(String mode) {
        prefsEditor.putString(OPERATING_MODE, mode);
        prefsEditor.apply();
    }

    public OperatingMode getOperatingMode() {
        return OperatingMode.FULL_SIGNALS.name().equals(appSharedPrefs.getString(OPERATING_MODE,
                OperatingMode.NO_SIGNALS.name())) ? OperatingMode.FULL_SIGNALS : OperatingMode.NO_SIGNALS;
    }

    public void activateCamera(boolean active) {
        prefsEditor.putBoolean(CAMERA_ACTIVE, active);
        prefsEditor.apply();
    }

    public boolean getCameraActivation() {
        return appSharedPrefs.getBoolean(CAMERA_ACTIVE, true);
    }

    public void setCamera(String camera) {
        prefsEditor.putString(CAMERA, camera);
        prefsEditor.apply();
    }

    public String getCamera() {
        return appSharedPrefs.getString(CAMERA, FRONT);
    }

    public void setCameraSensitivity(int sensitivity) {
        prefsEditor.putInt(CAMERA_SENSITIVITY, sensitivity);
        prefsEditor.apply();
    }

    public int getCameraSensitivity() {
        return appSharedPrefs.getInt(CAMERA_SENSITIVITY, LuminanceMotionDetector.MOTION_MEDIUM);
    }

    public void activateFlash(boolean active) {
        prefsEditor.putBoolean(FLASH_ACTIVE, active);
        prefsEditor.apply();
    }

    public boolean getFlashActivation() {
        return appSharedPrefs.getBoolean(FLASH_ACTIVE, false);
    }

    public void activateMicrophone(boolean active) {
        prefsEditor.putBoolean(MICROPHONE_ACTIVE, active);
        prefsEditor.apply();
    }

    public boolean getMicrophoneActivation() {
        return appSharedPrefs.getBoolean(MICROPHONE_ACTIVE, true);
    }

    public void setMicrophoneSensitivity(String sensitivity) {
        prefsEditor.putString(MICROPHONE_SENSITIVITY, sensitivity);
        prefsEditor.apply();
    }

    public String getMicrophoneSensitivity() {
        return appSharedPrefs.getString(MICROPHONE_SENSITIVITY, MEDIUM);
    }

    public void setRemoteNotificationActive(boolean isRemoteNotificationActive) {
        prefsEditor.putBoolean(REMOTE_NOTIFICATION_ACTIVE, isRemoteNotificationActive);
        prefsEditor.apply();
    }

    public boolean isRemoteNotificationActive() {
        return appSharedPrefs.getBoolean(REMOTE_NOTIFICATION_ACTIVE, false);
    }

    public void setRemotePhoneNumber(@NonNull String remotePhoneNumber) {
        prefsEditor.putString(REMOTE_PHONE_NUMBER, remotePhoneNumber.trim());
        prefsEditor.apply();
    }

    @NonNull
    public String getRemotePhoneNumber() {
        return Objects.requireNonNull(appSharedPrefs.getString(REMOTE_PHONE_NUMBER, \"\"));
    }

    public int getTimerDelay () {
        return appSharedPrefs.getInt(TIMER_DELAY,30);
    }

    public void setTimerDelay (int delay) {
        prefsEditor.putInt(TIMER_DELAY,delay);
        prefsEditor.apply();
    }

    public int getMonitoringTime () {
        return appSharedPrefs.getInt(VIDEO_LENGTH,30);
    }

    public void setMonitoringTime (int delay) {
        prefsEditor.putInt(VIDEO_LENGTH,delay);
        prefsEditor.apply();
    }

    public void setVoiceVerification(boolean active) {
        prefsEditor.putBoolean(VOICE_VERIFY_SIGNAL, active);
        prefsEditor.apply();
    }

    public boolean getVoiceVerificationEnabled() {
        return appSharedPrefs.getBoolean(VOICE_VERIFY_SIGNAL, false);
    }

    public String getDirPath() {
        return DIR_PATH;
    }

    public String getSMSText() {
        return context.getString(R.string.intrusion_detected);
    }

    public int getMaxImages () {
        return 10;
    }

    public String getBaseStoragePath() {
        return appSharedPrefs.getString(CONFIG_BASE_STORAGE,CONFIG_BASE_STORAGE_DEFAULT);
    }

    public String getDefaultMediaStoragePath() {
        return appSharedPrefs.getString(CONFIG_BASE_STORAGE,CONFIG_BASE_STORAGE_DEFAULT) + File.separator + getCurrentSession(); //phoneypot is the old code name for Haven
    }

    public void setDefaultMediaStoragePath (String path) {
        prefsEditor.putString(CONFIG_BASE_STORAGE,path);
        prefsEditor.apply();
    }

    public int getAudioLength () {
        return 15000; //30 seconds
    }

    public int getNotificationTimeMs () {
        return appSharedPrefs.getInt(NOTIFICATION_TIME,-1); //time in minutes times by seconds
    }

    public void setNotificationTimeMs (int notificationTimeMs) {
        prefsEditor.putInt(NOTIFICATION_TIME,notificationTimeMs);
        prefsEditor.apply();
    }

    public void activateHeartbeat(boolean active) {
        prefsEditor.putBoolean(HEARTBEAT_MONITOR_ACTIVE, active);
        prefsEditor.apply();
    }

    public void setHeartbeatMonitorNotifications (int notificationTimeMs) {
        prefsEditor.putInt(HEARTBEAT_MONITOR_DELAY,notificationTimeMs);
        prefsEditor.apply();
    }

    public boolean getHeartbeatActive() {
        return appSharedPrefs.getBoolean(HEARTBEAT_MONITOR_ACTIVE, false);
    }

    public int getHeartbeatNotificationTimeMs () {
        return appSharedPrefs.getInt(HEARTBEAT_MONITOR_DELAY,300000);
    }

    public String getHeartbeatMonitorMessage () {
        return appSharedPrefs.getString(HEARTBEAT_MONITOR_MESSAGE,null);
    }

    public void setHeartbeatMonitorMessage (String heartbeatMessage) {
        prefsEditor.putString(HEARTBEAT_MONITOR_MESSAGE, heartbeatMessage);
        prefsEditor.apply();
    }

    public String getHeartbeatPrefix() {
        return context.getString(R.string.heartbeat_monitor_initial_message_1);
    }

    public String getHeartbeatSuffix() {
        return context.getString(R.string.heartbeat_monitor_initial_message_2);
    }

    public void setCurrentSession(Date startTime) {
        prefsEditor.putString(CURRENT_EVENT_START_TIME, Utils.getDateTime(startTime));
        prefsEditor.apply();
    }

    private String getCurrentSession() {
        return appSharedPrefs.getString(CURRENT_EVENT_START_TIME, \"unknown_session\");
    }
}
