package s3backup.util

import software.amazon.awssdk.transfer.s3.progress.TransferListener
import java.math.BigDecimal
import java.math.RoundingMode

class SimpleProgressListener : TransferListener {
    private var lastMessageLength = 0

    override fun transferInitiated(context: TransferListener.Context.TransferInitiated) {
        context.progressSnapshot().ratioTransferred().ifPresent { ratio ->
            updateProgress(ratio)
        }
    }

    override fun bytesTransferred(context: TransferListener.Context.BytesTransferred) {
        context.progressSnapshot().ratioTransferred().ifPresent { ratio ->
            updateProgress(ratio)
        }
    }

    override fun transferComplete(context: TransferListener.Context.TransferComplete) {
        context.progressSnapshot().ratioTransferred().ifPresent { ratio ->
            updateProgress(ratio)
        }
        println() // New line after completion
        println("Transfer complete!")
    }

    override fun transferFailed(context: TransferListener.Context.TransferFailed) {
        println() // New line after failure
        println("Transfer failed: ${context.exception().message}")
        context.exception().printStackTrace()
    }

    private fun updateProgress(ratio: Double) {
        val percentage = round(ratio * 100, 1)
        val message = "${percentage}%..."
        // Clear previous line by overwriting with spaces, then print new message
        val clearSpaces = " ".repeat(maxOf(0, lastMessageLength - message.length))
        print("\r$message$clearSpaces")
        lastMessageLength = message.length
    }

    private fun round(value: Double, places: Int): Double {
        var bd = BigDecimal.valueOf(value)
        bd = bd.setScale(places, RoundingMode.FLOOR)
        return bd.toDouble()
    }
}
