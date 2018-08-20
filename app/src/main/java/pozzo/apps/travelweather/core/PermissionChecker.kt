package pozzo.apps.travelweather.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.content.ContextCompat

class PermissionChecker(private val context: Context) {

    fun hasPermission(permission: String) : Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1
            || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
