package no.nav.hag.utils.bakgrunnsjobb

import no.nav.hag.utils.bakgrunnsjobb.processing.AutoCleanJobbProcessor

internal object BakgrunnsjobbTable {
    private const val TABLE_NAME = "bakgrunnsjobb"

    val insertStatement =
        """
        INSERT INTO $TABLE_NAME (
            jobb_id,
            type,
            behandlet,
            opprettet,
            status,
            kjoeretid,
            forsoek,
            maks_forsoek,
            data
        ) VALUES (
            ?::UUID,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?::JSON
        )
        """.trimExcessWhitespace()

    val updateStatement =
        """
        UPDATE $TABLE_NAME
        SET behandlet = ?,
            status = ?,
            kjoeretid = ?,
            forsoek = ?,
            data = ?::JSON
        WHERE jobb_id = ?::UUID
        """.trimExcessWhitespace()

    val selectByIdStatement =
        """
        SELECT *
        FROM $TABLE_NAME
        WHERE jobb_id = ?::UUID
        """.trimExcessWhitespace()

    val selectStatement =
        """
        SELECT *
        FROM $TABLE_NAME
        WHERE kjoeretid < ?
          AND status = ANY(?)
        """.trimExcessWhitespace()

    val selectWithLimitStatement = "$selectStatement LIMIT 100".trimExcessWhitespace()

    val selectAutoCleanStatement =
        """
        SELECT *
        FROM $TABLE_NAME
        WHERE status IN (
                  '${Bakgrunnsjobb.Status.OPPRETTET}',
                  '${Bakgrunnsjobb.Status.FEILET}'
              )
          AND type = '${AutoCleanJobbProcessor.Companion.JOB_TYPE}'
        """.trimExcessWhitespace()

    val selectOkAutoCleanStatement =
        """
        SELECT *
        FROM $TABLE_NAME
        WHERE status = '${Bakgrunnsjobb.Status.OK}'
          AND type = '${AutoCleanJobbProcessor.Companion.JOB_TYPE}'
        """.trimExcessWhitespace()

    val deleteStatement =
        """
        DELETE
        FROM $TABLE_NAME
        WHERE jobb_id = ?::UUID
        """.trimExcessWhitespace()

    val deleteAllStatement =
        """
        DELETE
        FROM $TABLE_NAME
        """.trimExcessWhitespace()

    val deleteOldOkJobsStatement =
        """
        DELETE
        FROM $TABLE_NAME
        WHERE status = '${Bakgrunnsjobb.Status.OK}'
          AND behandlet < ?
        """.trimExcessWhitespace()

    private fun String.trimExcessWhitespace() = replace(Regex("\\s+"), " ").trim()
}
