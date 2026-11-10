package dev.termos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone as PhoneKind
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

data class Contact(val name: String, val number: String)
data class Msg(val addr: String, val date: Long, val body: String)

object Comms {
    const val P_CONTACTS = Manifest.permission.READ_CONTACTS
    const val P_READ_SMS = Manifest.permission.READ_SMS
    const val P_SEND_SMS = Manifest.permission.SEND_SMS

    fun has(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun contacts(c: Context, q: String, limit: Int = 40): List<Contact> {
        val out = LinkedHashMap<String, Contact>()
        val sel = if (q.isBlank()) null else "${PhoneKind.DISPLAY_NAME} LIKE ?"
        val args = if (q.isBlank()) null else arrayOf("%$q%")
        try {
            c.contentResolver.query(PhoneKind.CONTENT_URI, arrayOf(PhoneKind.DISPLAY_NAME, PhoneKind.NUMBER), sel, args, "${PhoneKind.DISPLAY_NAME} ASC")?.use { cur ->
                while (cur.moveToNext() && out.size < limit) {
                    val name = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    out.putIfAbsent(name + "|" + PhoneNum.normalize(num), Contact(name, num))
                }
            }
        } catch (e: Exception) { }
        return out.values.toList()
    }

    fun resolve(c: Context, who: String): Contact? {
        if (PhoneNum.looksLikeNumber(who)) return Contact(who.trim(), PhoneNum.normalize(who))
        if (!has(c, P_CONTACTS)) return null
        return contacts(c, who, 1).firstOrNull()
    }

    fun nameFor(c: Context, addr: String): String {
        if (!has(c, P_CONTACTS)) return addr
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(addr))
            c.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) return it.getString(0) ?: addr
            }
        } catch (e: Exception) { }
        return addr
    }

    fun inbox(c: Context, limit: Int, fromNumber: String? = null): List<Msg> {
        val sel = if (fromNumber.isNullOrBlank()) null else "${Telephony.Sms.ADDRESS} LIKE ?"
        val args = if (fromNumber.isNullOrBlank()) null else arrayOf("%$fromNumber%")
        val out = mutableListOf<Msg>()
        try {
            c.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY),
                sel, args, "${Telephony.Sms.DATE} DESC"
            )?.use { cur ->
                while (cur.moveToNext() && out.size < limit) out.add(Msg(cur.getString(0) ?: "?", cur.getLong(1), cur.getString(2) ?: ""))
            }
        } catch (e: Exception) { }
        return out
    }

    @Suppress("DEPRECATION")
    private fun manager(c: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

    fun send(c: Context, number: String, text: String) {
        val sm = manager(c)
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
    }
}
