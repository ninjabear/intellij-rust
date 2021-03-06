package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiDirectory
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.names.RustQualifiedName
import org.rust.lang.core.names.parts.RustIdNamePart
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustModItemImplMixin : RustItemImpl
                                    , RustModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val items: List<RustItem> get() = itemList

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.MODULE)

    override val `super`: RustMod get() = requireNotNull(parentOfType()) {
        "No parent mod for non-file mod at ${containingFile.virtualFile.path}:\n$text"
    }

    override val ownsDirectory: Boolean = true // Any inline nested mod owns a directory

    override val ownedDirectory: PsiDirectory? get() {
        val name = name ?: return null
        return `super`.ownedDirectory?.findSubdirectory(name)
    }

    override val isCrateRoot: Boolean = false

    override val isTopLevelInFile: Boolean = false

    override val canonicalNameInFile: RustQualifiedName?
        get() = name?.let { RustQualifiedName(RustIdNamePart(it), `super`.canonicalNameInFile) }

    override val modDecls: Collection<RustModDeclItem>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RustModDeclItem::class.java)

    override val declarations: Collection<RustDeclaringElement>
        get() = items
}
