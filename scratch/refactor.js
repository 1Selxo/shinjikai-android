const fs = require('fs');

const appPath = 'c:\\Users\\almag\\shinjikai.app\\app\\src\\main\\java\\com\\shinjikai\\dictionary\\ShinjikaiApp.kt';
let content = fs.readFileSync(appPath, 'utf8');

// Imports
content = content.replace("import androidx.lifecycle.viewmodel.compose.viewModel\n",
`import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel\n`);

// Setup
content = content.replace("val viewModel: ShinjikaiViewModel = viewModel()",
`val viewModel: ShinjikaiViewModel = hiltViewModel()
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route ?: Screen.Search.name`);

// BackHandler & Handlers
content = content.replace("BackHandler(enabled = screenStack.size > 1) { viewModel.goBack() }", "");

const newHandlers = `val handleSearchTabClick = {
                if (currentRoute == Screen.Search.name) {
                    viewModel.focusSearchField()
                } else {
                    focusManager.clearFocus()
                    navController.navigate(Screen.Search.name) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            val navToHistory = {
                focusManager.clearFocus()
                navController.navigate(Screen.History.name) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            }
            val navToBookmarks = {
                focusManager.clearFocus()
                navController.navigate(Screen.Bookmarks.name) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            }
            val navToSettings = {
                focusManager.clearFocus()
                navController.navigate(Screen.Settings.name) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            }
            val navToLocalDictionary = {
                focusManager.clearFocus()
                navController.navigate(Screen.LocalDictionary.name)
            }
            val navToDetail = {
                focusManager.clearFocus()
                navController.navigate(Screen.Detail.name)
            }`;

const startHandleMatch = "val handleSearchTabClick = {";
const endHandleMatch = "viewModel.openPrimaryScreen(Screen.Search)\n                }\n            }";
const firstSlice = content.substring(0, content.indexOf(startHandleMatch));
const secondSlice = content.substring(content.indexOf(endHandleMatch) + endHandleMatch.length);
content = firstSlice + newHandlers + secondSlice;

// Update navigation actions
content = content.replace(/viewModel\.openPrimaryScreen\(Screen\.History\)/g, "navToHistory()");
content = content.replace(/viewModel\.openPrimaryScreen\(Screen\.Bookmarks\)/g, "navToBookmarks()");
content = content.replace(/viewModel\.openPrimaryScreen\(Screen\.Settings\)/g, "navToSettings()");
content = content.replace(/viewModel\.openPrimaryScreen\(Screen\.LocalDictionary\)/g, "navToLocalDictionary()");

content = content.replace(/viewModel\.openDetails\(it\)/g, "viewModel.openDetails(it); navToDetail()");
content = content.replace(/viewModel::goBack/g, "navController::popBackStack");

// AnimatedContent replace with NavHost
const transitionBlock = `AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            when {
                                initialState == Screen.Search && targetState == Screen.Detail -> {
                                    (fadeIn(animationSpec = tween(180, delayMillis = 60)) +
                                        slideInVertically(animationSpec = tween(220)) { it / 18 }) togetherWith
                                        (fadeOut(animationSpec = tween(140)) +
                                            slideOutVertically(animationSpec = tween(180)) { it / 10 })
                                }
                                initialState == Screen.Detail && targetState == Screen.Search -> {
                                    (fadeIn(animationSpec = tween(180)) +
                                        slideInVertically(animationSpec = tween(220)) { -it / 18 }) togetherWith
                                        (fadeOut(animationSpec = tween(140)) +
                                            slideOutVertically(animationSpec = tween(180)) { -it / 10 })
                                }
                                else -> {
                                    fadeIn(animationSpec = tween(160)) togetherWith
                                        fadeOut(animationSpec = tween(120))
                                }
                            }
                        },
                        label = "screen-transition"
                    ) { screen ->
                        when (screen) {
                            Screen.Search -> `;
// Just locate "when (screen) {" roughly and replace safely
if (content.includes("AnimatedContent(")) {
    const beforeBlock = content.substring(0, content.indexOf("AnimatedContent("));
    const afterBlockStart = content.indexOf("Screen.Search ->");
    if (afterBlockStart !== -1) {
        let replacementBlock = `NavHost(navController = navController, startDestination = Screen.Search.name) {
                        composable(Screen.Search.name) {
                            `;
        let remaining = content.substring(afterBlockStart + "Screen.Search ->".length);
        
        remaining = remaining.replace("Screen.History ->", "}\n                        composable(Screen.History.name) {");
        remaining = remaining.replace("Screen.Bookmarks ->", "}\n                        composable(Screen.Bookmarks.name) {");
        remaining = remaining.replace("Screen.Settings ->", "}\n                        composable(Screen.Settings.name) {");
        remaining = remaining.replace("Screen.LocalDictionary ->", "}\n                        composable(Screen.LocalDictionary.name) {");
        remaining = remaining.replace("Screen.Detail ->", "}\n                        composable(Screen.Detail.name) {");
        
        // Final two braces from `when (screen) { ... }` need to be addressed. NavHost needs one bracket.
        // We can just rely on the original closing brackets of AnimatedContent. 
        // AnimatedContent ends with "} }". NavHost ends with "}". So it's 1 brace short.
        
        content = beforeBlock + replacementBlock + remaining;
        // Strip out the extra branch brace "}" for `when(screen)` 
        content = content.replace(/}\n                    }\n                }\n\n            }\n        }\n    }\n}/g, "                }\n\n            }\n        }\n    }\n}");
    }
}

content = content.replace("val currentScreen", "//val currentScreen");
content = content.replace("currentScreen == Screen.Settings", "currentRoute == Screen.Settings.name");


fs.writeFileSync(appPath, content);
console.log("Refactor complete.");
