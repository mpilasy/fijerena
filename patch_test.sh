cat << 'INNER_EOF' > pt.diff
--- core/ui/src/test/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchUtilsTest.kt
+++ core/ui/src/test/java/org/njarasoa/fijerena/core/ui/viewmodels/SearchUtilsTest.kt
@@ -17,41 +17,41 @@
     fun matchesQuery_findsMatches() {
         val text = "This is a Hello World example"
-        val queryWords = listOf("hello", "world")
-        assertTrue(SearchUtils.matchesQuery(text, queryWords))
+        val queryWords = SearchUtils.ParsedQuery(listOf("hello", "world"), emptyList())
+        assertTrue(SearchUtils.matchesQuery(text, queryWords))
     }

     @Test
     fun matchesQuery_failsOnPartialMatch() {
         val text = "This is a Hello example"
-        val queryWords = listOf("hello", "world")
-        assertFalse(SearchUtils.matchesQuery(text, queryWords))
+        val queryWords = SearchUtils.ParsedQuery(listOf("hello", "world"), emptyList())
+        assertFalse(SearchUtils.matchesQuery(text, queryWords))
     }

     @Test
     fun matchesQuery_emptyQuery_returnsTrue() {
         val text = "Any text"
-        val queryWords = emptyList<String>()
-        assertTrue(SearchUtils.matchesQuery(text, queryWords))
+        val queryWords = SearchUtils.ParsedQuery(emptyList(), emptyList())
+        assertTrue(SearchUtils.matchesQuery(text, queryWords))
     }

     @Test
     fun matchesQuery_negativeSearch_excludesMatch() {
         val text = "This is a Hello World example"
-        val queryWords = listOf("hello", "-world")
-        assertFalse(SearchUtils.matchesQuery(text, queryWords))
+        val queryWords = SearchUtils.ParsedQuery(listOf("hello"), listOf("world"))
+        assertFalse(SearchUtils.matchesQuery(text, queryWords))
     }

     @Test
     fun matchesQuery_negativeSearch_includesNoMatch() {
         val text = "This is a Hello example"
-        val queryWords = listOf("hello", "-world")
-        assertTrue(SearchUtils.matchesQuery(text, queryWords))
+        val queryWords = SearchUtils.ParsedQuery(listOf("hello"), listOf("world"))
+        assertTrue(SearchUtils.matchesQuery(text, queryWords))
     }

     @Test
     fun matchesQuery_negativeSearch_ignoresSingleDash() {
         val text = "This is a Hello - example"
-        val queryWords = listOf("hello", "-")
-        assertTrue(SearchUtils.matchesQuery(text, queryWords))
+        val queryWords = SearchUtils.ParsedQuery(listOf("hello", "-"), emptyList())
+        assertTrue(SearchUtils.matchesQuery(text, queryWords))
     }
 }
INNER_EOF
patch -p0 < pt.diff
