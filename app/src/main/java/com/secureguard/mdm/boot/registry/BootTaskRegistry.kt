package com.secureguard.mdm.boot.registry

import com.secureguard.mdm.boot.api.BootTask
import com.secureguard.mdm.boot.impl.ShowToastOnBootTask
import javax.inject.Inject
import javax.inject.Singleton

/**
 * מרשם מרכזי לכל המשימות שצריכות לרוץ בעת אתחול המכשיר.
 * ה-MainService משתמש ברשימה זו כדי להפעיל את כל המשימות הנדרשות.
 *
 * @param showToastOnBootTask המימוש של המשימה להצגת התראה (מוזרק על ידי Hilt).
 */
@Singleton
class BootTaskRegistry @Inject constructor(
    showToastOnBootTask: ShowToastOnBootTask
    // בעתיד, ניתן להזריק לכאן משימות נוספות
) {
    /**
     * הרשימה המפורשת של כל משימות האתחול.
     * הסדר ברשימה זו יכול לקבוע את סדר ההרצה אם יהיה בכך צורך.
     */
    val allBootTasks: List<BootTask> = listOf(
        showToastOnBootTask
        // כאן ניתן להוסיף משימות עתידיות
    )
}