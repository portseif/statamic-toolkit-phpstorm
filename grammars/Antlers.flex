package com.antlers.support.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.antlers.support.lexer.AntlersTokenTypes.*;

%%

%class _AntlersLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
    private int braceDepth = 0;

    private void resetBraceDepth() {
        braceDepth = 0;
    }
%}

// Shared character classes
ALPHA = [a-zA-Z_]
ALNUM = [a-zA-Z0-9_]
DIGIT = [0-9]
WHITE = [ \t\n\r]+

// States
%state ANTLERS_EXPR
%state COMMENT
%state PHP_RAW
%state PHP_ECHO
%state DQ_STRING
%state SQ_STRING
%state NOPARSE

%%

// ============================================================
// YYINITIAL: HTML content, detecting Antlers delimiters
// ============================================================
<YYINITIAL> {
    // Escaped antlers: @{{ — consume as template text (must be before {{ rules)
    "@{{"                     { return ESCAPED_CONTENT; }

    // Comment open: {{#
    "{{#"                     { yybegin(COMMENT); return COMMENT_OPEN; }

    // PHP raw open: {{?
    "{{?"                     { yybegin(PHP_RAW); return PHP_RAW_OPEN; }

    // PHP echo open: {{$
    "{{$"                     { yybegin(PHP_ECHO); return PHP_ECHO_OPEN; }

    // Antlers expression open: {{
    "{{"                      { resetBraceDepth(); yybegin(ANTLERS_EXPR); return ANTLERS_OPEN; }

    // HTML content: anything that is not the start of an Antlers delimiter
    // Match one or more characters that aren't '{' or '@'
    [^{@]+                    { return TEMPLATE_TEXT; }

    // A single '{' that isn't followed by another '{'
    "{"                       { return TEMPLATE_TEXT; }

    // A single '@' that isn't followed by '{{'
    "@"                       { return TEMPLATE_TEXT; }
}

// ============================================================
// COMMENT: inside {{# ... #}}
// ============================================================
<COMMENT> {
    "#}}"                     { yybegin(YYINITIAL); return COMMENT_CLOSE; }
    [^#]+                     { return COMMENT_CONTENT; }
    "#" / [^}]                { return COMMENT_CONTENT; }
    "#" / "$"                 { return COMMENT_CONTENT; }
    "#"                       { return COMMENT_CONTENT; }
}

// ============================================================
// PHP_RAW: inside {{? ... ?}}
// ============================================================
<PHP_RAW> {
    "?}}"                     { yybegin(YYINITIAL); return PHP_RAW_CLOSE; }
    [^?]+                     { return PHP_RAW_CONTENT; }
    "?" / [^}]                { return PHP_RAW_CONTENT; }
    "?" / "$"                 { return PHP_RAW_CONTENT; }
    "?"                       { return PHP_RAW_CONTENT; }
}

// ============================================================
// PHP_ECHO: inside {{$ ... $}}
// ============================================================
<PHP_ECHO> {
    "$}}"                     { yybegin(YYINITIAL); return PHP_ECHO_CLOSE; }
    [^$]+                     { return PHP_ECHO_CONTENT; }
    "$" / [^}]                { return PHP_ECHO_CONTENT; }
    "$" / "$"                 { return PHP_ECHO_CONTENT; }
    "$"                       { return PHP_ECHO_CONTENT; }
}

// ============================================================
// NOPARSE: inside {{ noparse }} ... {{ /noparse }}
// ============================================================
<NOPARSE> {
    "{{" {WHITE}? "/" {WHITE}? "noparse" {WHITE}? "}}"  { yybegin(YYINITIAL); return ANTLERS_CLOSE; }
    [^{]+                     { return NOPARSE_CONTENT; }
    "{"                       { return NOPARSE_CONTENT; }
}

// ============================================================
// ANTLERS_EXPR: inside {{ ... }}
// ============================================================
<ANTLERS_EXPR> {
    // Self-closing tag: /}}
    "/}}"                     { yybegin(YYINITIAL); return TAG_SELF_CLOSE; }

    // Close delimiter: }}
    "}}"                      { yybegin(YYINITIAL); return ANTLERS_CLOSE; }

    // Whitespace
    {WHITE}                   { return WHITE_SPACE; }

    // Strings — transition to string states
    \"                        { yybegin(DQ_STRING); return STRING_DQ; }
    \'                        { yybegin(SQ_STRING); return STRING_SQ; }

    // Keywords (must be checked before identifiers)
    // Word boundary: followed by non-alphanumeric
    "elseif" / [^a-zA-Z0-9_] { return KEYWORD_ELSEIF; }
    "else" / [^a-zA-Z0-9_]   { return KEYWORD_ELSE; }
    "endif" / [^a-zA-Z0-9_]  { return KEYWORD_ENDIF; }
    "endunless" / [^a-zA-Z0-9_] { return KEYWORD_ENDUNLESS; }
    "if" / [^a-zA-Z0-9_]     { return KEYWORD_IF; }
    "unless" / [^a-zA-Z0-9_] { return KEYWORD_UNLESS; }
    "switch" / [^a-zA-Z0-9_] { return KEYWORD_SWITCH; }
    "noparse" / [^a-zA-Z0-9_] { yybegin(NOPARSE); return KEYWORD_NOPARSE; }
    "once" / [^a-zA-Z0-9_]   { return KEYWORD_ONCE; }
    "true" / [^a-zA-Z0-9_]   { return KEYWORD_TRUE; }
    "false" / [^a-zA-Z0-9_]  { return KEYWORD_FALSE; }
    "null" / [^a-zA-Z0-9_]   { return KEYWORD_NULL; }
    "void" / [^a-zA-Z0-9_]   { return KEYWORD_VOID; }
    "as" / [^a-zA-Z0-9_]     { return KEYWORD_AS; }
    "and" / [^a-zA-Z0-9_]    { return KEYWORD_AND; }
    "or" / [^a-zA-Z0-9_]     { return KEYWORD_OR; }
    "xor" / [^a-zA-Z0-9_]    { return KEYWORD_XOR; }
    "not" / [^a-zA-Z0-9_]    { return KEYWORD_NOT; }
    "where" / [^a-zA-Z0-9_]  { return KEYWORD_WHERE; }
    "orderby" / [^a-zA-Z0-9_] { return KEYWORD_ORDERBY; }
    "groupby" / [^a-zA-Z0-9_] { return KEYWORD_GROUPBY; }
    "merge" / [^a-zA-Z0-9_]  { return KEYWORD_MERGE; }
    "take" / [^a-zA-Z0-9_]   { return KEYWORD_TAKE; }
    "skip" / [^a-zA-Z0-9_]   { return KEYWORD_SKIP; }
    "pluck" / [^a-zA-Z0-9_]  { return KEYWORD_PLUCK; }

    // Keywords at end of expression (before }})
    "else" / {WHITE}? "}}"   { return KEYWORD_ELSE; }
    "true" / {WHITE}? "}}"   { return KEYWORD_TRUE; }
    "false" / {WHITE}? "}}"  { return KEYWORD_FALSE; }
    "null" / {WHITE}? "}}"   { return KEYWORD_NULL; }
    "void" / {WHITE}? "}}"   { return KEYWORD_VOID; }

    // Identifiers: variable names, tag names
    {ALPHA} {ALNUM}* ("-" {ALNUM}+)*  { return IDENTIFIER; }

    // Numbers
    {DIGIT}+ "." {DIGIT}+    { return NUMBER_FLOAT; }
    {DIGIT}+                  { return NUMBER_INT; }

    // Multi-character operators (longest match first)
    "==="                     { return OP_IDENTICAL; }
    "!=="                     { return OP_NOT_IDENTICAL; }
    "<=>"                     { return OP_SPACESHIP; }
    "**"                      { return OP_POWER; }
    "??"                      { return OP_NULL_COALESCE; }
    "?="                      { return OP_GATEKEEPER; }
    "&&"                      { return OP_AND; }
    "||"                      { return OP_OR; }
    "=="                      { return OP_EQUALS; }
    "!="                      { return OP_NOT_EQUALS; }
    ">="                      { return OP_GREATER_EQUAL; }
    "<="                      { return OP_LESS_EQUAL; }
    "+="                      { return OP_PLUS_ASSIGN; }
    "-="                      { return OP_MINUS_ASSIGN; }
    "*="                      { return OP_MULTIPLY_ASSIGN; }
    "/="                      { return OP_DIVIDE_ASSIGN; }
    "%="                      { return OP_MODULO_ASSIGN; }
    "=>"                      { return OP_ARROW; }
    ".."                      { return OP_RANGE; }

    // Single-character operators
    "="                       { return OP_ASSIGN; }
    "+"                       { return OP_PLUS; }
    "-"                       { return OP_MINUS; }
    "*"                       { return OP_MULTIPLY; }
    "/"                       { return OP_DIVIDE; }
    "%"                       { return OP_MODULO; }
    "!"                       { return OP_NOT; }
    ">"                       { return OP_GREATER; }
    "<"                       { return OP_LESS; }
    "?"                       { return OP_TERNARY_QUESTION; }
    "|"                       { return OP_PIPE; }

    // Punctuation
    "."                       { return DOT; }
    ":"                       { return COLON; }
    ";"                       { return SEMICOLON; }
    ","                       { return COMMA; }
    "("                       { return LPAREN; }
    ")"                       { return RPAREN; }
    "["                       { return LBRACKET; }
    "]"                       { return RBRACKET; }
    "$"                       { return DOLLAR; }
    "@"                       { return AT; }

    // Anything else inside an expression
    [^]                       { return BAD_CHARACTER; }
}

// ============================================================
// DQ_STRING: inside double-quoted string within an expression
// ============================================================
<DQ_STRING> {
    // Escaped characters inside string
    "\\\""                    { return STRING_DQ; }
    "\\\\"                    { return STRING_DQ; }

    // End of string
    \"                        { yybegin(ANTLERS_EXPR); return STRING_DQ; }

    // String content — anything that isn't a quote or backslash
    [^\"\\]+                  { return STRING_DQ; }

    // Single backslash followed by something other than quote or backslash
    "\\"                      { return STRING_DQ; }
}

// ============================================================
// SQ_STRING: inside single-quoted string within an expression
// ============================================================
<SQ_STRING> {
    // Escaped characters
    "\\'"                     { return STRING_SQ; }
    "\\\\"                    { return STRING_SQ; }

    // End of string
    \'                        { yybegin(ANTLERS_EXPR); return STRING_SQ; }

    // String content
    [^\'\\]+                  { return STRING_SQ; }

    // Single backslash
    "\\"                      { return STRING_SQ; }
}
