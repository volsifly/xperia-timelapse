package com.xperia.timelapse.power

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class CaptureLockAdmin:DeviceAdminReceiver()

class CaptureLockManager(context:Context) {
 private val manager=context.getSystemService(DevicePolicyManager::class.java)
 private val component=ComponentName(context,CaptureLockAdmin::class.java)
 val authorized get()=manager.isAdminActive(component)
 fun authorizationIntent()=Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
  .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,component)
  .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"允许拍摄开始 30 秒后自动锁屏。锁屏后继续拍摄，只申请锁屏能力。")
 fun lock() {check(authorized) {"自动锁屏尚未授权"};manager.lockNow()}
}
