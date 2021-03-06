package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.toolchain.RustToolchain

object RustCrateUtil


/**
 * Extracts paths ot the Crate's roots'
 */
val Module.crateRoots: Collection<VirtualFile>
    get() = cargoProject?.packages.orEmpty()
                .flatMap    { it.targets }
                .mapNotNull { it.virtualFile } + standardLibraryCrates.map { it.virtualFile }


data class ExternCrate(
    /**
     * Name of a crate as appears in `extern crate foo;`
     */
    val name: String,

    /**
     * Root module file (typically `src/lib.rs`)
     */
    val virtualFile: VirtualFile
)

/**
 * Searches for the PsiFile of the root mod of the crate.
 */
fun Module.findExternCrateByName(crateName: String): PsiFile? =
    externCrates.find { it.name == crateName }?.let {
        PsiManager.getInstance(project).findFile(it.virtualFile)
    }

val Module.preludeModule: PsiFile? get() {
    val stdlib = findExternCrateByName(AutoInjectedCrates.std) ?: return null
    val preludeFile = stdlib.virtualFile.findFileByRelativePath("../prelude/v1.rs") ?: return null
    return PsiManager.getInstance(project).findFile(preludeFile)
}

/**
 * A set of external crates for the module. External crate can refer
 * to another module or a library or a crate form the SDK
 */
private val Module.externCrates: Collection<ExternCrate> get() =
    cargoProject?.packages.orEmpty().mapNotNull { pkg ->
        pkg.libTarget?.virtualFile?.let { ExternCrate(pkg.name, it) }
    } + standardLibraryCrates

object AutoInjectedCrates {
    const val std: String = "std"
    const val core: String = "core"
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }

/**
 * Extracts Cargo project description out of `Cargo.toml`
 */
val Module.cargoProject: CargoProjectDescription?
    get() = getComponentOrThrow<CargoProjectWorkspace>().projectDescription
