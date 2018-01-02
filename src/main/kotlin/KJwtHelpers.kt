@file:Suppress("unused")

import eu.webtoolkit.jwt.*


// for all box layouts where widgets can be added simply
open class KJwtBox(type: Int, parent: WContainerWidget): WContainerWidget(parent) {
    private val mylayout = when(type) {
        0 -> WHBoxLayout(this)
        1 -> WVBoxLayout(this)
        else -> throw IllegalStateException("wrong box type $type")
    }
    init {
        this.layout = mylayout
    }
    fun addit(child: WWidget, stretch: Int = 0) {
        mylayout.addWidget(child, stretch)
    }
}

class KJwtHBox(parent: WContainerWidget): KJwtBox(0, parent)
class KJwtVBox(parent: WContainerWidget): KJwtBox(1, parent)

fun kJwtHBox(parent: WContainerWidget, body: KJwtHBox.() -> Unit): KJwtHBox {
    val hb = KJwtHBox(parent)
    body(hb)
    return hb
}

fun kJwtVBox(parent: WContainerWidget, body: KJwtVBox.() -> Unit): KJwtVBox {
    val hb = KJwtVBox(parent)
    body(hb)
    return hb
}

fun <T>kJwtGeneric(factory: () -> T, body: T.() -> Unit): T {
    val hb = factory()
    body(hb)
    return hb
}

fun doUI(app: WApplication, f: () -> Unit) {
    val uiLock = app.updateLock
    f()
    app.triggerUpdate()
    uiLock?.release()
}

class KWPushButton(text: CharSequence, tooltip: String, myclicked: WPushButton.() -> Unit): WPushButton(text) {
    init {
        this.clicked().addListener(this, Signal.Listener { myclicked(this) })
        this.setToolTip(tooltip)
    }
}
