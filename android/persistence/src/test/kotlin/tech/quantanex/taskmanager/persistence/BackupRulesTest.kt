package tech.quantanex.taskmanager.persistence

import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {
    @Test
    fun backupRulesExcludeEncryptedDatabaseSidecarsKeyWrappersAndSensitiveDiagnostics() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val legacyRules = readXmlResource(context.resources.getXml(R.xml.backup_rules))
        val dataExtractionRules = readXmlResource(context.resources.getXml(R.xml.data_extraction_rules))
        val combined = legacyRules + dataExtractionRules

        listOf(
            "task-manager.db",
            "task-manager.db-wal",
            "task-manager.db-shm",
            "task-manager.db-journal",
            "task-manager.db.schema",
            "task-manager.db-schema",
            "task-manager.db-fts",
            "task-manager-secrets/",
            "task-manager-diagnostics/",
            "screenshots/",
            "task-manager-sensitive.xml",
        ).forEach { sensitivePath ->
            assertTrue(
                combined.any { it.contains("path=$sensitivePath") },
                "Missing backup/data-extraction exclusion for $sensitivePath",
            )
        }
    }

    private fun readXmlResource(parser: XmlResourceParser): List<String> = buildList {
        parser.use {
            var eventType = parser.eventType
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG && parser.name == "exclude") {
                    val domain = parser.getAttributeValue(null, "domain")
                    val path = parser.getAttributeValue(null, "path")
                    add("domain=$domain path=$path")
                }
                eventType = parser.next()
            }
        }
    }
}
