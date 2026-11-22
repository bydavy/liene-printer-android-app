package io.github.bydavy.liene

import android.app.Application
import io.github.bydavy.liene.printer.PrinterRepository

class LieneApplication : Application() {
    val repository: PrinterRepository by lazy { PrinterRepository(this) }
}
