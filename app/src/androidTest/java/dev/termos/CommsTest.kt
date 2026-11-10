package dev.termos

import android.Manifest
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

/** AC-25 .. AC-27 : contacts and SMS flows (permissions pre-granted; nothing is really sent). */
class CommsTest : TermTest() {
    @get:Rule
    val perms: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS
    )

    @Test fun ac25_contactsFilterWithNoMatch() {
        login(); type("contacts zzqqxx")
        screenHas("no contacts match")
    }

    @Test fun ac26_smsAsksBeforeSending() {
        login(); type("sms send +251900000000 hello there")
        screenHas("send? answer y/n")
        type("n"); screenHas("sms cancelled")
    }

    @Test fun ac27_smsReadNeedsAListFirst() {
        login(); type("sms 3")
        type("sms read 99"); screenHas("no such message")
    }
}
