package com.antlers.support.actions

data class StatamicInsertTemplate(
    val title: String,
    val description: String,
    val content: String,
    val docsUrl: String
)

object StatamicSnippetTemplates {
    val entryQuery = StatamicInsertTemplate(
        title = "Entry Query",
        description = "Query a collection and return a limited set of entries.",
        content = """
            ${'$'}entries = \Statamic\Facades\Entry::query()
                ->where('collection', 'blog')
                ->limit(5)
                ->get();
            
        """.trimIndent(),
        docsUrl = "https://statamic.dev/frontend/content-queries"
    )

    val singleEntryQuery = StatamicInsertTemplate(
        title = "Single Entry Query",
        description = "Fetch one entry matching a collection and slug.",
        content = """
            ${'$'}entry = \Statamic\Facades\Entry::query()
                ->where('collection', 'blog')
                ->where('slug', 'hello-world')
                ->first();
            
        """.trimIndent(),
        docsUrl = "https://statamic.dev/frontend/content-queries"
    )

    val paginatedEntriesQuery = StatamicInsertTemplate(
        title = "Paginated Entry Query",
        description = "Build a paginated list of published entries.",
        content = """
            ${'$'}paginator = \Statamic\Facades\Entry::query()
                ->where('collection', 'blog')
                ->where('published', true)
                ->paginate(12);
            
        """.trimIndent(),
        docsUrl = "https://statamic.dev/frontend/content-queries"
    )

    val globalSetQuery = StatamicInsertTemplate(
        title = "Global Set Lookup",
        description = "Read a value from a Statamic global set.",
        content = """
            ${'$'}copyright = \Statamic\Facades\GlobalSet::findByHandle('footer')
                ?->inDefaultSite()
                ?->get('copyright');
            
        """.trimIndent(),
        docsUrl = "https://statamic.dev/frontend/content-queries"
    )

    val contentQueries: List<StatamicInsertTemplate> = listOf(
        entryQuery,
        singleEntryQuery,
        paginatedEntriesQuery,
        globalSetQuery
    )

    fun normalizeControllerClassName(rawName: String): String? {
        val parts = rawName
            .trim()
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .map { part ->
                part.replaceFirstChar { char -> char.titlecase() }
            }

        if (parts.isEmpty()) return null

        val joined = parts.joinToString("")
        return if (joined.endsWith("Controller")) joined else "${joined}Controller"
    }

    fun buildBasicController(className: String): String = """
        <?php
        
        namespace App\Http\Controllers;
        
        use App\Http\Controllers\Controller;
        
        class $className extends Controller
        {
            public function index()
            {
                ${'$'}data = [
                    'title' => 'Example Title',
                ];
        
                return view('myview', ${'$'}data);
            }
        }
        
    """.trimIndent()

    fun buildAntlersViewController(className: String): String = """
        <?php
        
        namespace App\Http\Controllers;
        
        use App\Http\Controllers\Controller;
        use Statamic\View\View;
        
        class $className extends Controller
        {
            public function index()
            {
                return (new View)
                    ->template('myview')
                    ->layout('mylayout')
                    ->with([
                        'title' => 'Example Title',
                    ]);
            }
        }
        
    """.trimIndent()
}
