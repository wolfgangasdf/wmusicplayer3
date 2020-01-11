import eu.webtoolkit.jwt.*
import java.util.*


// for all box layouts where widgets can be added simply
@Suppress("LeakingThis")
open class KJwtBox(type: Int, parent: WContainerWidget): WContainerWidget(parent) {
    private val mylayout = when(type) {
        0 -> WHBoxLayout()
        1 -> WVBoxLayout()
        else -> throw IllegalStateException("wrong box type $type")
    }
    init {
        mylayout.setContentsMargins(0,0,0,0)
        this.layout = mylayout
    }
    fun addit(child: WWidget, stretch: Int = 0, alignmentFlag: EnumSet<AlignmentFlag> = EnumSet.noneOf(AlignmentFlag::class.java)) {
        mylayout.addWidget(child, stretch, alignmentFlag)
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

fun <T>kJwtGeneric(factory: () -> T, body: T.() -> Unit = {}): T {
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

class KWTableView : WTableView() {
    var onLayoutSizeChanged: (w: Int, h: Int) -> Unit = { _: Int, _: Int -> }
    override fun layoutSizeChanged(width: Int, height: Int) {
        onLayoutSizeChanged(width, height)
        super.layoutSizeChanged(width, height)
    }

    init {
        isLayoutSizeAware = true
    }
}

// binds javafx property to JWT widget, with initialization of widget!
fun <T>bindprop2widget(app: WApplication, prop: javafx.beans.property.Property<T>, update: (oldv: T?, newv: T) -> Unit) {
    update(null, prop.value)
    prop.addListener { _, oldval, newval -> doUI(app) { update(oldval, newval) } }
}
