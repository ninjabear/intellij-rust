package org.rust.ide.documentation

class RustQuickDocumentationTest : RustDocumentationProviderTest() {
    override val dataPath = "org/rust/ide/documentation/fixtures/doc"

    fun testFn() = checkDoc()

    private fun checkDoc() = compareByHtml { element, originalElement ->
        RustDocumentationProvider().generateDoc(element, originalElement)
    }
}

