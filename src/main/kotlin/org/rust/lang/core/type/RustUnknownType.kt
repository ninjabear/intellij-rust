package org.rust.lang.core.type

import com.intellij.psi.PsiManager
import org.rust.lang.core.psi.RustImplItem

class RustUnknownType(manager: PsiManager) : RustResolvedTypeBase(manager) {
    override val inheritedImplsInner: Collection<RustImplItem> = emptyList()
    override fun toString(): String = "<unknown type>"
}
