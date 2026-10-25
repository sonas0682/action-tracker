package com.sonas.actiontracker

import com.sonas.actiontracker.sync.GmailLink
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailLinkTest {
    @Test fun webUrlPointsAtTheMessage() {
        assertEquals("https://mail.google.com/mail/u/0/#all/18c2f0a9b7e4d123", GmailLink.webUrl("18c2f0a9b7e4d123"))
    }
}
