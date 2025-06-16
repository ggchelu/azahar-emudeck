// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.fragments

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.R
import org.citra.citra_emu.adapters.SetupAdapter
import org.citra.citra_emu.databinding.FragmentSetupBinding
import org.citra.citra_emu.features.settings.model.Settings
import org.citra.citra_emu.model.ButtonState
import org.citra.citra_emu.model.PageButton
import org.citra.citra_emu.model.PageState
import org.citra.citra_emu.model.SetupCallback
import org.citra.citra_emu.model.SetupPage
import org.citra.citra_emu.ui.main.MainActivity
import org.citra.citra_emu.utils.AutomaticDirectoryHelper
import org.citra.citra_emu.utils.CitraDirectoryHelper
import org.citra.citra_emu.utils.GameHelper
import org.citra.citra_emu.utils.Log
import org.citra.citra_emu.utils.PermissionsHandler
import org.citra.citra_emu.utils.ViewUtils
import org.citra.citra_emu.viewmodel.GamesViewModel
import org.citra.citra_emu.viewmodel.HomeViewModel

class SetupFragment : Fragment() {
    private var _binding: FragmentSetupBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val gamesViewModel: GamesViewModel by activityViewModels()

    private lateinit var mainActivity: MainActivity

    private lateinit var hasBeenWarned: BooleanArray

    private lateinit var pages: MutableList<SetupPage>

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)

    companion object {
        const val KEY_NEXT_VISIBILITY = "NextButtonVisibility"
        const val KEY_BACK_VISIBILITY = "BackButtonVisibility"
        const val KEY_HAS_BEEN_WARNED = "HasBeenWarned"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainActivity = requireActivity() as MainActivity

        homeViewModel.setNavigationVisibility(visible = false, animated = false)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.viewPager2.currentItem > 0) {
                        pageBackward()
                    } else {
                        requireActivity().finish()
                    }
                }
            }
        )

        requireActivity().window.navigationBarColor =
            ContextCompat.getColor(requireContext(), android.R.color.transparent)

        pages = mutableListOf()
        pages.apply {
            add(
                SetupPage(
                    R.drawable.ic_citra_full,
                    R.string.welcome,
                    R.string.welcome_description,
                    0,
                    true,
                    R.string.get_started,
                    pageButtons = mutableListOf<PageButton>().apply {
                        add(
                            PageButton(
                                R.drawable.ic_arrow_forward,
                                R.string.get_started,
                                0,
                                buttonAction = {
                                    pageForward()
                                },
                                buttonState = {
                                    ButtonState.BUTTON_ACTION_UNDEFINED
                                }
                            )
                        )
                    }
                )
            )

            add(
                SetupPage(
                    R.drawable.ic_permission,
                    R.string.permissions,
                    R.string.permissions_description,
                    0,
                    false,
                    0,
                    pageButtons = mutableListOf<PageButton>().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(
                                PageButton(
                                    R.drawable.ic_notification,
                                    R.string.notifications,
                                    R.string.notifications_description,
                                    buttonAction = {
                                        pageButtonCallback = it
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    buttonState = {
                                        if (NotificationManagerCompat.from(requireContext())
                                                .areNotificationsEnabled()
                                        ) {
                                            ButtonState.BUTTON_ACTION_COMPLETE
                                        } else {
                                            ButtonState.BUTTON_ACTION_INCOMPLETE
                                        }
                                    },
                                    isUnskippable = false,
                                    hasWarning = true,
                                    R.string.notification_warning,
                                    R.string.notification_warning_description,
                                )
                            )
                        }
                        add(
                            PageButton(
                                R.drawable.ic_microphone,
                                R.string.microphone_permission,
                                R.string.microphone_permission_description,
                                buttonAction = {
                                    pageButtonCallback = it
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                buttonState = {
                                    if (ContextCompat.checkSelfPermission(
                                            requireContext(),
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        ButtonState.BUTTON_ACTION_COMPLETE
                                    } else {
                                        ButtonState.BUTTON_ACTION_INCOMPLETE
                                    }
                                },
                            )
                        )
                        add(
                            PageButton(
                                R.drawable.ic_camera,
                                R.string.camera_permission,
                                R.string.camera_permission_description,
                                buttonAction = {
                                    pageButtonCallback = it
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                buttonState = {
                                    if (ContextCompat.checkSelfPermission(
                                            requireContext(),
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        ButtonState.BUTTON_ACTION_COMPLETE
                                    } else {
                                        ButtonState.BUTTON_ACTION_INCOMPLETE
                                    }
                                },
                            )
                        )
                    },
                ) {
                    if (
                        ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED &&
                        NotificationManagerCompat.from(requireContext())
                            .areNotificationsEnabled()
                    ) {
                        PageState.PAGE_STEPS_COMPLETE
                    } else {
                        PageState.PAGE_STEPS_INCOMPLETE
                    }
                }
            )

            add(
                SetupPage(
                    R.drawable.ic_folder,
                    R.string.select_emulator_data_folders,
                    R.string.select_emulator_data_folders_description,
                    0,
                    true,
                    R.string.select,
                    pageButtons = mutableListOf<PageButton>().apply {
                        add(
                            PageButton(
                                R.drawable.ic_home,
                                R.string.select_citra_user_folder,
                                R.string.select_citra_user_folder_description,
                                buttonAction = {
                                    pageButtonCallback = it
                                    attemptAutomaticDirectorySetup()
                                },
                                buttonState = {
                                    if (PermissionsHandler.hasWriteAccess(requireContext())) {
                                        ButtonState.BUTTON_ACTION_COMPLETE
                                    } else {
                                        // Also check if our automatic directory setup has been completed
                                        val azaharUri = PermissionsHandler.citraDirectory
                                        if (azaharUri.toString().isNotEmpty() && 
                                            azaharUri.scheme == "file" &&
                                            java.io.File(azaharUri.path ?: "").exists()) {
                                            ButtonState.BUTTON_ACTION_COMPLETE
                                        } else {
                                            ButtonState.BUTTON_ACTION_INCOMPLETE
                                        }
                                    }
                                },
                                isUnskippable = true,
                                hasWarning = false,
                                R.string.cannot_skip,
                                R.string.cannot_skip_directory_description,
                                R.string.cannot_skip_directory_help

                            )
                        )
                        add(
                            PageButton(
                                R.drawable.ic_controller,
                                R.string.games,
                                R.string.games_description,
                                buttonAction =  {
                                    pageButtonCallback = it
                                    attemptAutomaticGamesDirectorySetup()
                                },
                                buttonState = {
                                    if (preferences.getString(GameHelper.KEY_GAME_PATH, "")!!.isNotEmpty()) {
                                        ButtonState.BUTTON_ACTION_COMPLETE
                                    } else {
                                        ButtonState.BUTTON_ACTION_INCOMPLETE
                                    }
                                },
                                isUnskippable = false,
                                hasWarning = true,
                                R.string.add_games_warning,
                                R.string.add_games_warning_description,
                            )
                        )
                    },
                ) {
                    val hasValidDirectory = if (PermissionsHandler.hasWriteAccess(requireContext())) {
                        true
                    } else {
                        // Also check if our automatic directory setup has been completed
                        val azaharUri = PermissionsHandler.citraDirectory
                        azaharUri.toString().isNotEmpty() && 
                        azaharUri.scheme == "file" &&
                        java.io.File(azaharUri.path ?: "").exists()
                    }
                    
                    if (hasValidDirectory &&
                        preferences.getString(GameHelper.KEY_GAME_PATH, "")!!.isNotEmpty()
                    ) {
                        PageState.PAGE_STEPS_COMPLETE
                    } else {
                        PageState.PAGE_STEPS_INCOMPLETE
                    }
                }
            )

            add(
                SetupPage(
                    R.drawable.ic_check,
                    R.string.done,
                    R.string.done_description,
                    R.drawable.ic_arrow_forward,
                    false,
                    R.string.text_continue,
                    pageButtons = mutableListOf<PageButton>().apply {
                        add(
                            PageButton(
                                R.drawable.ic_arrow_forward,
                                R.string.text_continue,
                                0,
                                buttonAction = {
                                    finishSetup()
                                },
                                buttonState = {
                                    ButtonState.BUTTON_ACTION_UNDEFINED
                                }
                            )
                        )
                    }
                )
            )
        }

        binding.viewPager2.apply {
            adapter = SetupAdapter(requireActivity() as AppCompatActivity, pages)
            offscreenPageLimit = 2
            isUserInputEnabled = false
        }

        binding.viewPager2.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            var previousPosition: Int = 0

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                if (position == 1 && previousPosition == 0) {
                    ViewUtils.showView(binding.buttonNext)
                    ViewUtils.showView(binding.buttonBack)
                } else if (position == 0 && previousPosition == 1) {
                    ViewUtils.hideView(binding.buttonBack)
                    ViewUtils.hideView(binding.buttonNext)
                } else if (position == pages.size - 1 && previousPosition == pages.size - 2) {
                    ViewUtils.hideView(binding.buttonNext)
                } else if (position == pages.size - 2 && previousPosition == pages.size - 1) {
                    ViewUtils.showView(binding.buttonNext)
                }

                previousPosition = position
            }
        })

        binding.buttonNext.setOnClickListener {
            val index = binding.viewPager2.currentItem
            val currentPage = pages[index]

            // This allows multiple sets of warning messages to be displayed on the same dialog if necessary
            val warningMessages =
                mutableListOf<Triple<Int, Int, Int>>() // title, description, helpLink

            currentPage.pageButtons?.forEach { button ->
                if (button.hasWarning || button.isUnskippable) {
                    val buttonState = button.buttonState()
                    if (buttonState == ButtonState.BUTTON_ACTION_COMPLETE) {
                        return@forEach
                    }

                    if (button.isUnskippable) {
                        MessageDialogFragment.newInstance(
                            button.warningTitleId,
                            button.warningDescriptionId,
                            button.warningHelpLinkId
                        ).show(childFragmentManager, MessageDialogFragment.TAG)
                        return@setOnClickListener
                    }

                    if (!hasBeenWarned[index]) {
                        warningMessages.add(
                            Triple(
                                button.warningTitleId,
                                button.warningDescriptionId,
                                button.warningHelpLinkId
                            )
                        )
                    }
                }
            }

            if (warningMessages.isNotEmpty()) {
                SetupWarningDialogFragment.newInstance(
                    warningMessages.map { it.first }.toIntArray(),
                    warningMessages.map { it.second }.toIntArray(),
                    warningMessages.map { it.third }.toIntArray(),
                    index
                ).show(childFragmentManager, SetupWarningDialogFragment.TAG)
                return@setOnClickListener
            }
            pageForward()
        }
        binding.buttonBack.setOnClickListener { pageBackward() }

        if (savedInstanceState != null) {
            val nextIsVisible = savedInstanceState.getBoolean(KEY_NEXT_VISIBILITY)
            val backIsVisible = savedInstanceState.getBoolean(KEY_BACK_VISIBILITY)
            hasBeenWarned = savedInstanceState.getBooleanArray(KEY_HAS_BEEN_WARNED)!!

            if (nextIsVisible) {
                binding.buttonNext.visibility = View.VISIBLE
            }
            if (backIsVisible) {
                binding.buttonBack.visibility = View.VISIBLE
            }
        } else {
            hasBeenWarned = BooleanArray(pages.size)
        }

        setInsets()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_NEXT_VISIBILITY, binding.buttonNext.isVisible)
        outState.putBoolean(KEY_BACK_VISIBILITY, binding.buttonBack.isVisible)
        outState.putBooleanArray(KEY_HAS_BEEN_WARNED, hasBeenWarned)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private lateinit var pageButtonCallback: SetupCallback
    private val checkForButtonState: () -> Unit = {
        val page = pages[binding.viewPager2.currentItem]
        page.pageButtons?.forEach {
            if (it.buttonState() == ButtonState.BUTTON_ACTION_COMPLETE) {
                pageButtonCallback.onStepCompleted(it.titleId, pageFullyCompleted = false)
            }

            if (page.pageSteps() == PageState.PAGE_STEPS_COMPLETE) {
                pageButtonCallback.onStepCompleted(0, pageFullyCompleted = true)
            }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Continue automatic setup after permission is granted
                continueAutomaticDirectorySetup()
                return@registerForActivityResult
            }

            Snackbar.make(binding.root, R.string.permission_denied, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.buttonNext)
                .setAction(R.string.grid_menu_core_settings) {
                    val intent =
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", requireActivity().packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .show()
        }

    private val openCitraDirectory = registerForActivityResult<Uri, Uri>(
        ActivityResultContracts.OpenDocumentTree()
    ) { result: Uri? ->
        if (result == null) {
            return@registerForActivityResult
        }

        CitraDirectoryHelper(requireActivity(), true).showCitraDirectoryDialog(result, pageButtonCallback, checkForButtonState)
    }

    private val getGamesDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            requireActivity().contentResolver.takePersistableUriPermission(
                result,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // When a new directory is picked, we currently will reset the existing games
            // database. This effectively means that only one game directory is supported.
            preferences.edit()
                .putString(GameHelper.KEY_GAME_PATH, result.toString())
                .apply()

            homeViewModel.setGamesDir(requireActivity(), result.path!!)

            checkForButtonState.invoke()
        }

    private fun attemptAutomaticDirectorySetup() {
        try {
            // Check if we have storage permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (requireContext().checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Request permission - will continue in permissionLauncher callback
                    permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    return
                }
            }
            
            // Permission granted, continue with setup
            continueAutomaticDirectorySetup()
        } catch (e: Exception) {
            // Fall back to manual directory selection on any error
            android.util.Log.e("SetupFragment", "Error during automatic setup: ${e.message}")
            openCitraDirectory.launch(null)
        }
    }

    private fun continueAutomaticDirectorySetup() {
        try {
            // Check if automatic setup is possible
            if (!AutomaticDirectoryHelper.canCreateAutomatically(requireContext())) {
                android.util.Log.w("SetupFragment", "Cannot create automatically, falling back to manual")
                // Fall back to manual selection immediately
                openCitraDirectory.launch(null)
                return
            }
            
            // Try automatic directory creation
            val (success, azaharUri) = AutomaticDirectoryHelper.createAzaharDirectoryAutomatically(requireContext())
            
            if (success && azaharUri != null) {
                // Set persistent URI permission (required for external storage access)
                val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    requireActivity().contentResolver.takePersistableUriPermission(azaharUri, takeFlags)
                } catch (e: Exception) {
                    // This might fail for file:// URIs, but that's okay for external files dir
                    android.util.Log.d("SetupFragment", "Could not take persistent permission (expected for file URIs): ${e.message}")
                }
                
                // Initialize the directory in the system - this sets PermissionsHandler.citraDirectory
                CitraDirectoryHelper.initializeCitraDirectory(azaharUri)
                
                // Set up home view model
                homeViewModel.setUserDir(requireActivity(), azaharUri.path!!)
                homeViewModel.setPickingUserDir(false)
                
                // Create Games subdirectory automatically
                val gamesUri = AutomaticDirectoryHelper.createGamesDirectory(requireContext(), azaharUri)
                if (gamesUri != null) {
                    preferences.edit()
                        .putString(GameHelper.KEY_GAME_PATH, gamesUri.toString())
                        .apply()
                    homeViewModel.setGamesDir(requireActivity(), gamesUri.path!!)
                }
                
                // Mark setup as complete
                checkForButtonState.invoke()
                
                // Show simple success message
                android.widget.Toast.makeText(
                    requireContext(),
                    "Azahar setup complete! Folder created at: ${azaharUri.path}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
            } else {
                android.util.Log.w("SetupFragment", "Automatic creation failed, falling back to manual")
                // Fall back to manual directory selection
                openCitraDirectory.launch(null)
            }
        } catch (e: Exception) {
            // Fall back to manual directory selection on any error
            android.util.Log.e("SetupFragment", "Error during automatic setup continuation: ${e.message}")
            openCitraDirectory.launch(null)
        }
    }

    private fun attemptAutomaticGamesDirectorySetup() {
        try {
            // Check if Azahar directory exists first
            val azaharUri = PermissionsHandler.citraDirectory
            if (azaharUri.toString().isEmpty()) {
                // No Azahar directory set, show manual selection
                getGamesDirectory.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).data)
                return
            }
            
            // Create Games subdirectory automatically
            val gamesUri = AutomaticDirectoryHelper.createGamesDirectory(requireContext(), azaharUri)
            if (gamesUri != null) {
                preferences.edit()
                    .putString(GameHelper.KEY_GAME_PATH, gamesUri.toString())
                    .apply()
                
                homeViewModel.setGamesDir(requireActivity(), gamesUri.path!!)
                checkForButtonState.invoke()
                
            } else {
                // Fall back to manual selection
                getGamesDirectory.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).data)
            }
        } catch (e: Exception) {
            // Fall back to manual selection on any error
            Log.error("[SetupFragment] Error during games directory setup: ${e.message}")
            getGamesDirectory.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).data)
        }
    }

    private fun finishSetup() {
        preferences.edit()
            .putBoolean(Settings.PREF_FIRST_APP_LAUNCH, false)
            .apply()
        mainActivity.finishSetup(binding.root.findNavController())
    }

    fun pageForward() {
        binding.viewPager2.currentItem = binding.viewPager2.currentItem + 1
    }

    fun pageBackward() {
        binding.viewPager2.currentItem = binding.viewPager2.currentItem - 1
    }

    fun setPageWarned(page: Int) {
        hasBeenWarned[page] = true
    }

    private fun setInsets() =
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _: View, windowInsets: WindowInsetsCompat ->
            val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val leftPadding = barInsets.left + cutoutInsets.left
            val topPadding = barInsets.top + cutoutInsets.top
            val rightPadding = barInsets.right + cutoutInsets.right
            val bottomPadding = barInsets.bottom + cutoutInsets.bottom

            if (resources.getBoolean(R.bool.small_layout)) {
                binding.viewPager2
                    .updatePadding(left = leftPadding, top = topPadding, right = rightPadding)
                binding.constraintButtons
                    .updatePadding(left = leftPadding, right = rightPadding, bottom = bottomPadding)
            } else {
                binding.viewPager2.updatePadding(top = topPadding, bottom = bottomPadding)
                binding.constraintButtons
                    .setPadding(
                        leftPadding + rightPadding,
                        topPadding,
                        rightPadding + leftPadding,
                        bottomPadding
                    )
            }
            windowInsets
        }
}
