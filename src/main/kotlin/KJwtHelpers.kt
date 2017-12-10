import eu.webtoolkit.jwt.*


class KJwtHBox(parent: WContainerWidget): WContainerWidget(parent) {
    val mylayout = WHBoxLayout(this)
    init {
        this.layout = mylayout
    }
    fun addit(child: WWidget, stretch: Int = 0) {
        mylayout.addWidget(child, stretch)
    }
}

fun kJwtHBox(parent: WContainerWidget, body: KJwtHBox.() -> Unit): KJwtHBox {
    val hb = KJwtHBox(parent)
    body(hb)
    return hb
}

class KWPushButton(text: CharSequence, tooltip: String, clicked: () -> Unit): WPushButton(text) {
    init {
        this.clicked().addListener(this, Signal.Listener { clicked() })
        this.setToolTip(tooltip)
    }
}
