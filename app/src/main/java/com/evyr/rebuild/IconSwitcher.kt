package com.evyr.rebuild

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Flips which launcher identity is active -- NERV (default) or Redemption --
 * by enabling one activity-alias and disabling the other. Both aliases point
 * at the same MainActivity (see AndroidManifest.xml); Android resolves the
 * launcher icon/label from whichever alias is currently enabled.
 *
 * The home screen doesn't always repaint the icon/label immediately after
 * this call -- some launchers pick it up right away, others need the app
 * relaunched or the home screen to refresh on its own. That's OS/launcher
 * behavior, not something this app controls.
 */
enum class LauncherTheme { NERV, REDEMPTION }

object IconSwitcher {

    private const val NERV_ALIAS = "com.evyr.rebuild.LauncherNerv"
    private const val REDEMPTION_ALIAS = "com.evyr.rebuild.LauncherRedemption"

    fun currentTheme(context: Context): LauncherTheme {
        val pm = context.packageManager
        val nervEnabled = isEnabled(pm, context, NERV_ALIAS)
        return if (nervEnabled) LauncherTheme.NERV else LauncherTheme.REDEMPTION
    }

    fun setTheme(context: Context, theme: LauncherTheme) {
        val pm = context.packageManager
        when (theme) {
            LauncherTheme.NERV -> {
                setEnabled(pm, context, NERV_ALIAS, true)
                setEnabled(pm, context, REDEMPTION_ALIAS, false)
            }
            LauncherTheme.REDEMPTION -> {
                setEnabled(pm, context, NERV_ALIAS, false)
                setEnabled(pm, context, REDEMPTION_ALIAS, true)
            }
        }
    }

    private fun isEnabled(pm: PackageManager, context: Context, aliasName: String): Boolean {
        val component = ComponentName(context, aliasName)
        val setting = pm.getComponentEnabledSetting(component)
        // COMPONENT_ENABLED_STATE_DEFAULT means "whatever the manifest says" --
        // the NERV alias defaults to enabled="true" in the manifest, so treat
        // DEFAULT as enabled specifically for that one.
        return when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> aliasName == NERV_ALIAS
        }
    }

    private fun setEnabled(pm: PackageManager, context: Context, aliasName: String, enabled: Boolean) {
        val component = ComponentName(context, aliasName)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
