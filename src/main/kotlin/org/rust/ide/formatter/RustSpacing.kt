package org.rust.ide.formatter

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Block
import com.intellij.formatting.Spacing
import com.intellij.formatting.Spacing.createSpacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RustCompositeElementTypes.*
import org.rust.lang.core.psi.RustTokenElementTypes.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.containsEOL
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.getPrevNonCommentSibling
import com.intellij.psi.tree.TokenSet.create as ts

private val KEYWORDS = ts(*IElementType.enumerate { it is RustKeywordTokenType })
private val NO_SPACE_AROUND_OPS = ts(COLONCOLON, DOT, DOTDOT)
private val SPACE_AROUND_OPS = ts(AND, ANDAND, ANDEQ, ARROW, FAT_ARROW, DIV, DIVEQ, EQ, EQEQ,
    EXCLEQ, GT, LT, MINUSEQ, MUL, MULEQ, OR, OREQ, OROR, PLUSEQ, REM, REMEQ, XOR, XOREQ, MINUS, PLUS,
    GTGTEQ, GTGT, GTEQ, LTLTEQ, LTLT, LTEQ)
private val UNARY_OPS = ts(MINUS, MUL, EXCL, AND, ANDAND)
// PATH_PART because `Fn(A) -> R`
private val PAREN_LIST_HOLDERS = ts(PAREN_EXPR, TUPLE_EXPR, TUPLE_TYPE, PARAMETERS, VARIADIC_PARAMETERS, ARG_LIST,
    IMPL_METHOD_MEMBER, BARE_FN_TYPE, PATH, PAT_ENUM, PAT_TUP, ENUM_TUPLE_ARGS)
private val BRACK_LIST_HOLDERS = ts(VEC_TYPE, ARRAY_EXPR, INDEX_EXPR)
private val BRACE_LIST_HOLDERS = ts(USE_GLOB_LIST)
private val ANGLE_LIST_HOLDERS = ts(GENERIC_PARAMS, GENERIC_ARGS, QUAL_PATH_EXPR)
private val ATTRS = ts(OUTER_ATTR, INNER_ATTR)
private val BLOCK_LIKE = ts(BLOCK, STRUCT_DECL_ARGS, STRUCT_EXPR_BODY, IMPL_BODY, MATCH_BODY, TRAIT_BODY, ENUM_BODY,
    ENUM_STRUCT_ARGS)
private val TYPES = ts(VEC_TYPE, PTR_TYPE, REF_TYPE, BARE_FN_TYPE, TUPLE_TYPE, PATH_TYPE,
    TYPE_WITH_BOUNDS_TYPE, FOR_IN_TYPE, WILDCARD_TYPE)
private val MACRO_ARGS = ts(MACRO_ARG, FORMAT_MACRO_ARGS, TRY_MACRO_ARGS)
private val PARAMS_LIKE = ts(PARAMETERS, VARIADIC_PARAMETERS)

fun createSpacingBuilder(commonSettings: CommonCodeStyleSettings,
                         @Suppress("UNUSED_PARAMETER") rustSettings: RustCodeStyleSettings): SpacingBuilder {

    // Use `sbX` temporaries to work around
    // https://youtrack.jetbrains.com/issue/KT-12239

    val sb1 = SpacingBuilder(commonSettings)
        // Rules defined earlier have higher priority.
        // Beware of comments between blocks!

        //== some special operators
        // FIXME(jajakobyly): Doesn't work well with comments
        .afterInside(COMMA, ts(STRUCT_DECL_ARGS, ENUM_BODY)).spacing(1, 1, 1, true, 1)
        .afterInside(COMMA, ts(ENUM_STRUCT_ARGS, STRUCT_EXPR_BODY)).spacing(1, 1, 0, true, 1)
        .after(COMMA).spacing(1, 1, 0, true, 0)
        .before(COMMA).spaceIf(false)
        .after(COLON).spaceIf(true)
        .before(COLON).spaceIf(false)
        .after(SEMICOLON).spaceIf(true)
        .before(SEMICOLON).spaceIf(false)
        .afterInside(AND, ts(REF_TYPE, SELF_ARGUMENT, PAT_REG, PARAMETER)).spaces(0)
        .beforeInside(Q, TRY_EXPR).spaces(0)
        .afterInside(UNARY_OPS, UNARY_EXPR).spaces(0)

        //== attributes
        .aroundInside(ts(SHA, EXCL, LBRACK, RBRACK), ATTRS).spaces(0)
        .aroundInside(ts(LPAREN, RPAREN), META_ITEM).spaces(0)

        //== empty parens
        .between(LPAREN, RPAREN).spaceIf(false)
        .between(LBRACK, RBRACK).spaceIf(false)
        .between(LBRACE, RBRACE).spaceIf(false)
        .betweenInside(OR, OR, LAMBDA_EXPR).spaceIf(false)

        //== paren delimited lists
        // withinPairInside does not accept TokenSet as parent node set :(
        // and we cannot create our own, because RuleCondition stuff is private
        .afterInside(LPAREN, PAREN_LIST_HOLDERS).spacing(0, 0, 0, true, 0)
        .beforeInside(RPAREN, PAREN_LIST_HOLDERS).spacing(0, 0, 0, true, 0)
        .afterInside(LBRACK, BRACK_LIST_HOLDERS).spacing(0, 0, 0, true, 0)
        .beforeInside(RBRACK, BRACK_LIST_HOLDERS).spacing(0, 0, 0, true, 0)
        .afterInside(LBRACE, BRACE_LIST_HOLDERS).spacing(0, 0, 0, true, 0)
        .beforeInside(RBRACE, BRACE_LIST_HOLDERS).spacing(0, 0, 0, true, 0)
        .afterInside(LT, ANGLE_LIST_HOLDERS).spacing(0, 0, 0, false, 0)
        .beforeInside(GT, ANGLE_LIST_HOLDERS).spacing(0, 0, 0, false, 0)
        .aroundInside(OR, PARAMS_LIKE).spacing(0, 0, 0, false, 0)

    val sb2 = sb1
        //== items
        .between(PARAMS_LIKE, RET_TYPE).spacing(1, 1, 0, true, 0)
        .before(WHERE_CLAUSE).spacing(1, 1, 0, true, 0)
        .applyForEach(BLOCK_LIKE) { before(it).spaces(1) }
        .beforeInside(LBRACE, ts(FOREIGN_MOD_ITEM, MOD_ITEM)).spaces(1)

        .between(ts(IDENTIFIER, FN), PARAMS_LIKE).spaceIf(false)
        .between(IDENTIFIER, GENERIC_PARAMS).spaceIf(false)
        .between(IDENTIFIER, GENERIC_ARGS).spaceIf(false)
        .between(IDENTIFIER, ARG_LIST).spaceIf(false)
        .between(GENERIC_PARAMS, PARAMS_LIKE).spaceIf(false)
        .beforeInside(ARG_LIST, CALL_EXPR).spaceIf(false)

        .between(BINDING_MODE, IDENTIFIER).spaces(1)
        .between(IMPL, GENERIC_PARAMS).spaces(0)
        .afterInside(GENERIC_PARAMS, IMPL_ITEM).spaces(1)
        .betweenInside(ts(GENERIC_PARAMS), TYPES, IMPL_ITEM).spaces(1)

        // Handling blocks is pretty complicated. Do not tamper with
        // them too much and let rustfmt do all the pesky work.
        // Some basic transformation from in-line block to multi-line block
        // is also performed; see doc of #blockMustBeMultiLine() for details.
        .afterInside(LBRACE, BLOCK_LIKE).spacing(1, 1, 0, true, 0)
        .beforeInside(RBRACE, BLOCK_LIKE).spacing(1, 1, 0, true, 0)

        .betweenInside(IDENTIFIER, ALIAS, EXTERN_CRATE_ITEM).spaces(1)

        .betweenInside(IDENTIFIER, ENUM_TUPLE_ARGS, ENUM_VARIANT).spaces(0)
        .betweenInside(IDENTIFIER, ENUM_DISCRIMINANT, ENUM_VARIANT).spaces(1)

    return sb2
        //== types
        .afterInside(LIFETIME, REF_TYPE).spaceIf(true)
        .betweenInside(ts(MUL), ts(CONST, MUT), PTR_TYPE).spaces(0)
        .before(TYPE_PARAM_BOUNDS).spaces(0)
        .beforeInside(LPAREN, PATH).spaces(0)

        //== expressions
        .beforeInside(LPAREN, PAT_ENUM).spaces(0)
        .beforeInside(LBRACK, INDEX_EXPR).spaces(0)
        .afterInside(PARAMS_LIKE, LAMBDA_EXPR).spacing(1, 1, 0, true, 1)
        .between(MATCH_ARM, MATCH_ARM).spacing(0, 0, 1, true, 1)

        //== macros
        .betweenInside(IDENTIFIER, EXCL, MACRO_INVOCATION).spaces(0)
        .between(MACRO_INVOCATION, MACRO_ARGS).spaces(0)
        .betweenInside(MACRO_INVOCATION, IDENTIFIER, MACRO_DEFINITION).spaces(1)
        .betweenInside(IDENTIFIER, MACRO_ARG, MACRO_DEFINITION).spaces(1)

        //== rules with very large area of application
        .around(NO_SPACE_AROUND_OPS).spaces(0)
        .around(SPACE_AROUND_OPS).spaces(1)
        .around(KEYWORDS).spaces(1)
}

fun computeSpacing(parentBlock: Block, child1: Block?, child2: Block, ctx: RustFmtBlockContext): Spacing? {
    if (child1 is ASTBlock && child2 is ASTBlock) SpacingContext.create(child1, child2).apply {
        when {
            psi1 is RustOuterAttr && psi2 is PsiComment
            -> return one()

            psi1 is RustOuterAttr && (psi2 is RustOuterAttr || psi1.parent is RustItem)
                || psi1 is PsiComment && (psi2 is RustOuterAttr || psi1.getPrevNonCommentSibling() is RustOuterAttr)
            -> return lineBreak(keepBlankLines = 0)

            blockMustBeMultiLine()
            -> return lineBreak(keepBlankLines = 0)

            ncPsi1 is RustStmt && ncPsi2.isStmtOrExpr
            -> return lineBreak(
                keepLineBreaks = ctx.commonSettings.KEEP_LINE_BREAKS,
                keepBlankLines = ctx.commonSettings.KEEP_BLANK_LINES_IN_CODE)

            ncPsi1.isTopLevelItem && ncPsi2.isTopLevelItem
            -> return lineBreak(
                keepLineBreaks = ctx.commonSettings.KEEP_LINE_BREAKS,
                keepBlankLines = ctx.commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS)
        }
    }
    return ctx.spacingBuilder.getSpacing(parentBlock, child1, child2)
}

private data class SpacingContext(val node1: ASTNode,
                                  val node2: ASTNode,
                                  val psi1: PsiElement,
                                  val psi2: PsiElement,
                                  val elementType1: IElementType,
                                  val elementType2: IElementType,
                                  val parentPsi: PsiElement?,
                                  val ncPsi1: PsiElement,
                                  val ncPsi2: PsiElement) {
    companion object {
        fun create(child1: ASTBlock, child2: ASTBlock): SpacingContext {
            val node1 = child1.node
            val node2 = child2.node
            val psi1 = node1.psi
            val psi2 = node2.psi
            val elementType1 = psi1.node.elementType
            val elementType2 = psi2.node.elementType
            val parentPsi = psi1.parent
            val (ncPsi1, ncPsi2) = omitCommentBlocks(node1, psi1, node2, psi2)
            return SpacingContext(node1, node2, psi1, psi2, elementType1, elementType2, parentPsi, ncPsi1, ncPsi2)
        }

        /**
         * Handle blocks of comments to get proper spacing between items and statements
         */
        private fun omitCommentBlocks(node1: ASTNode, psi1: PsiElement,
                                      node2: ASTNode, psi2: PsiElement): Pair<PsiElement, PsiElement> =
            Pair(
                if (psi1 is PsiComment && node1.hasLineBreakAfterInSameParent()) {
                    psi1.getPrevNonCommentSibling() ?: psi1
                } else {
                    psi1
                },
                if (psi2 is PsiComment && node2.hasLineBreakBeforeInSameParent()) {
                    psi2.getNextNonCommentSibling() ?: psi2
                } else {
                    psi2
                }
            )
    }
}

private inline fun SpacingBuilder.applyForEach(
    tokenSet: TokenSet, block: SpacingBuilder.(IElementType) -> SpacingBuilder): SpacingBuilder {
    var self = this
    for (tt in tokenSet.types) {
        self = block(this, tt)
    }
    return self
}

private fun one(): Spacing = createSpacing(1, 1, 0, false, 0)

private fun lineBreak(minLineFeeds: Int = 1,
                      keepLineBreaks: Boolean = true,
                      keepBlankLines: Int = 1): Spacing =
    createSpacing(0, Int.MAX_VALUE, minLineFeeds, keepLineBreaks, keepBlankLines)

private val PsiElement.isTopLevelItem: Boolean
    get() = (this is RustItem || this is RustAttr) && this.parent is RustFile

private val PsiElement.isStmtOrExpr: Boolean
    get() = this is RustStmt || this is RustExpr

private fun ASTNode.hasLineBreakAfterInSameParent(): Boolean =
    treeNext != null && TreeUtil.findFirstLeaf(treeNext).isWhiteSpaceWithLineBreak()

private fun ASTNode.hasLineBreakBeforeInSameParent(): Boolean =
    treePrev != null && TreeUtil.findLastLeaf(treePrev).isWhiteSpaceWithLineBreak()

private fun ASTNode?.isWhiteSpaceWithLineBreak(): Boolean =
    this != null && elementType == WHITE_SPACE && containsEOL()

/**
 * Ensure that blocks are laid out multi-line when:
 *  1. one brace is placed as it's a single-line block while the other - multi-line
 *  2. there are 2 or more statements/expressions inside if it's a code block
 *  3. it's item's body block
 */
private fun SpacingContext.blockMustBeMultiLine(): Boolean {
    if (elementType1 != LBRACE && elementType2 != RBRACE) return false

    val lbrace = (if (elementType1 == LBRACE) node1 else TreeUtil.findSiblingBackward(node2, LBRACE)) ?: return false
    val rbrace = (if (elementType2 == RBRACE) node2 else TreeUtil.findSibling(node1, RBRACE)) ?: return false

    val lbraceIsNewline = lbrace.hasWhitespaceAfterIgnoringComments()
    val rbraceIsNewline = rbrace.hasWhitespaceBeforeIgnoringComments()
    if (lbraceIsNewline xor rbraceIsNewline) {
        return true // 1
    }

    val childrenCount = countNonWhitespaceASTNodesBetween(lbrace, rbrace)

    return when (parentPsi) {
        is RustBlock          -> childrenCount != 0 && (childrenCount >= 2 || parentPsi.parent is RustItem) // 2
        is RustStructDeclArgs,
        is RustEnumBody,
        is RustTraitBody,
        is RustModItem,
        is RustForeignModItem -> childrenCount != 0 // 3
        else                  -> false
    }
}

private fun countNonWhitespaceASTNodesBetween(left: ASTNode, right: ASTNode): Int {
    // TODO(jajakobyly): assert that left is before right and they are siblings
    var count = 0
    var next: ASTNode? = left
    while (next != null && next != right) {
        next = FormatterUtil.getNext(next, WHITE_SPACE)
        count += 1
    }
    return count - 1 // subtract right node
}

private fun ASTNode.hasWhitespaceAfterIgnoringComments(): Boolean {
    val lastWS = psi.getNextNonCommentSibling()?.prevSibling
    return lastWS is PsiWhiteSpace && lastWS.containsEOL()
}

private fun ASTNode.hasWhitespaceBeforeIgnoringComments(): Boolean {
    val lastWS = psi.getPrevNonCommentSibling()?.nextSibling
    return lastWS is PsiWhiteSpace && lastWS.containsEOL()
}
