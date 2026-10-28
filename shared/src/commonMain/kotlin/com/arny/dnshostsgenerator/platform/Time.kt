package com.arny.dnshostsgenerator.platform

/** Текущее время в миллисекундах эпохи: нужно для оценки срока действия сертификатов. */
expect fun nowEpochMillis(): Long
