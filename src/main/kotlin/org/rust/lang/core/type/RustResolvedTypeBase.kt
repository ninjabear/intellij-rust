package org.rust.lang.core.type

import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustImplItem

abstract class RustResolvedTypeBase(manager: PsiManager) : RustResolvedType, LightElement(manager, RustLanguage) {
    final override val inheritedImpls: Collection<RustImplItem> by lazy { inheritedImplsInner }

    abstract protected val inheritedImplsInner: Collection<RustImplItem>
}

