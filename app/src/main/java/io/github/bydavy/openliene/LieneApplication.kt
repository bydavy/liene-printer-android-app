package io.github.bydavy.openliene

import android.app.Application
import io.github.bydavy.openliene.printer.PrinterRepository

class LieneApplication : Application() {
    val repository: PrinterRepository by lazy { PrinterRepository(this) }
}
