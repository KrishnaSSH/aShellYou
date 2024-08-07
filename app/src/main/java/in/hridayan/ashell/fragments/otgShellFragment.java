package in.hridayan.ashell.fragments;

import static in.hridayan.ashell.utils.OtgUtils.MessageOtg.CONNECTING;
import static in.hridayan.ashell.utils.OtgUtils.MessageOtg.DEVICE_FOUND;
import static in.hridayan.ashell.utils.OtgUtils.MessageOtg.DEVICE_NOT_FOUND;
import static in.hridayan.ashell.utils.OtgUtils.MessageOtg.FLASHING;
import static in.hridayan.ashell.utils.OtgUtils.MessageOtg.INSTALLING_PROGRESS;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;
import com.cgutman.adblib.UsbChannel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import in.hridayan.ashell.R;
import in.hridayan.ashell.UI.BehaviorFAB;
import in.hridayan.ashell.UI.BehaviorFAB.FabExtendingOnScrollListener;
import in.hridayan.ashell.UI.KeyboardUtils;
import in.hridayan.ashell.activities.ExamplesActivity;
import in.hridayan.ashell.activities.SettingsActivity;
import in.hridayan.ashell.adapters.CommandsAdapter;
import in.hridayan.ashell.adapters.SettingsAdapter;
import in.hridayan.ashell.utils.Commands;
import in.hridayan.ashell.utils.OtgUtils;
import in.hridayan.ashell.utils.OtgUtils.Const;
import in.hridayan.ashell.utils.OtgUtils.MessageOtg;
import in.hridayan.ashell.utils.SettingsItem;
import in.hridayan.ashell.utils.Utils;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class otgShellFragment extends Fragment
    implements TextView.OnEditorActionListener, View.OnKeyListener {
  private Handler handler;
  private UsbDevice mDevice;
  private TextView tvStatus;
  private MaterialTextView logs;
  private AppCompatImageButton mCable;
  private AdbCrypto adbCrypto;
  private AdbConnection adbConnection;
  private UsbManager mManager;
  private BottomNavigationView mNav;
  private CommandsAdapter mCommandsAdapter;
  private LinearLayoutCompat terminalView;
  private MaterialButton mSettingsButton, mBookMarks, mHistoryButton;
  private RecyclerView mRecyclerViewCommands;
  private SettingsAdapter adapter;
  private TextInputLayout mCommandInput;
  private TextInputEditText mCommand;
  private FloatingActionButton mSendButton, mUndoButton;
  private ExtendedFloatingActionButton mPasteButton;
  private ScrollView scrollView;
  private AlertDialog mWaitingDialog;
  private String user = null;
  private final Handler mHandler = new Handler(Looper.getMainLooper());
  private boolean isKeyboardVisible, sendButtonClicked = false, isSendDrawable = false;
  private List<String> mHistory = null, mResult = null;
  private View view;
  private AdbStream stream;
  private Context context;

  private OnFragmentInteractionListener mListener;

  public interface OnFragmentInteractionListener {
    void onRequestReset();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mListener = null;
  }

  @Override
  public void onAttach(@NonNull Context mContext) {
    super.onAttach(mContext);
    context = mContext;
    if (context instanceof OnFragmentInteractionListener) {
      mListener = (OnFragmentInteractionListener) context;
    } else {
      throw new RuntimeException(
          context.toString() + " must implement OnFragmentInteractionListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    context = requireContext();
    view = inflater.inflate(R.layout.fragment_otg, container, false);

    List<SettingsItem> settingsList = new ArrayList<>();
    adapter = new SettingsAdapter(settingsList, requireContext());
    logs = view.findViewById(R.id.logs);
    mBookMarks = view.findViewById(R.id.bookmarks);
    mCable = view.findViewById(R.id.otg_cable);
    mCommand = view.findViewById(R.id.shell_command);
    mCommandInput = view.findViewById(R.id.shell_command_layout);
    mHistoryButton = view.findViewById(R.id.history);
    mManager = (UsbManager) requireActivity().getSystemService(Context.USB_SERVICE);
    mNav = view.findViewById(R.id.bottom_nav_bar);
    mPasteButton = view.findViewById(R.id.paste_button);
    mRecyclerViewCommands = view.findViewById(R.id.rv_commands);
    mSendButton = view.findViewById(R.id.send);
    mSettingsButton = view.findViewById(R.id.settings);
    scrollView = view.findViewById(R.id.scrollView);
    terminalView = view.findViewById(R.id.terminalView);
    mUndoButton = view.findViewById(R.id.fab_undo);

    mRecyclerViewCommands.addOnScrollListener(new FabExtendingOnScrollListener(mPasteButton));

    mRecyclerViewCommands.setLayoutManager(new LinearLayoutManager(requireActivity()));

    BehaviorFAB.pasteAndUndo(mPasteButton, mUndoButton, mCommand);

    KeyboardUtils.attachVisibilityListener(
        requireActivity(),
        new KeyboardUtils.KeyboardVisibilityListener() {

          public void onKeyboardVisibilityChanged(boolean visible) {
            isKeyboardVisible = visible;
            if (isKeyboardVisible) {
              mPasteButton.setVisibility(View.GONE);
              mUndoButton.setVisibility(View.GONE);
            } else {
              if (mPasteButton.getVisibility() == View.GONE && !sendButtonClicked) {
                setVisibilityWithDelay(mPasteButton, 100);
              }
            }
          }
        });

    if (isSendDrawable) {
      mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_send, requireActivity()));

      mSendButton.setOnClickListener(
          new View.OnClickListener() {

            @Override
            public void onClick(View v) {
              sendButtonClicked = true;
              mPasteButton.hide();
              mUndoButton.hide();
              if (adbConnection != null) {
                putCommand();
              } else {

                mCommandInput.setError(getString(R.string.device_not_connected));
                mCommandInput.setErrorIconDrawable(
                    Utils.getDrawable(R.drawable.ic_cancel, requireActivity()));
                mCommandInput.setErrorIconOnClickListener(
                    t -> {
                      mCommand.setText(null);
                    });

                Utils.alignMargin(mSendButton);
                Utils.alignMargin(mCable);

                mHistoryButton.setVisibility(View.VISIBLE);

                if (mHistory == null) {
                  mHistory = new ArrayList<>();
                }

                mHistory.add(mCommand.getText().toString());

                new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(getString(R.string.error))
                    .setMessage(getString(R.string.otg_not_connected))
                    .setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {})
                    .show();
              }
            }
          });

    } else {
      mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_help, requireActivity()));
      mSendButton.setOnClickListener(
          v -> {
            Intent examples = new Intent(requireActivity(), ExamplesActivity.class);
            startActivity(examples);
          });
    }

    // Logic for changing the command send button depending on the text on the EditText

    mBookMarks.setVisibility(
        Utils.getBookmarks(requireActivity()).size() > 0 ? View.VISIBLE : View.GONE);

    mCommand.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {

            isSendDrawable = mCommand.getText() != null;

            mCommandInput.setError(null);

            mBookMarks.setVisibility(
                Utils.getBookmarks(requireActivity()).size() > 0 ? View.VISIBLE : View.GONE);
          }

          @Override
          public void afterTextChanged(Editable s) {
            mCommand.requestFocus();

            String inputText = s.toString();
            if (inputText.isEmpty()) {

              mBookMarks.setVisibility(
                  Utils.getBookmarks(requireActivity()).size() > 0 ? View.VISIBLE : View.GONE);

              mCommandInput.setEndIconVisible(false);
              mSendButton.setImageDrawable(
                  Utils.getDrawable(R.drawable.ic_help, requireActivity()));

              mSendButton.setOnClickListener(
                  new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                      Intent examples = new Intent(requireActivity(), ExamplesActivity.class);
                      startActivity(examples);
                    }
                  });

            } else {

              new Handler(Looper.getMainLooper())
                  .post(
                      () -> {
                        if (s.toString().contains(" ") && s.toString().contains(".")) {
                          String[] splitCommands = {
                            s.toString().substring(0, lastIndexOf(s.toString(), ".")),
                            s.toString().substring(lastIndexOf(s.toString(), "."))
                          };

                          String packageNamePrefix;
                          if (splitCommands[0].contains(" ")) {
                            packageNamePrefix = splitPrefix(splitCommands[0], 1);
                          } else {
                            packageNamePrefix = splitCommands[0];
                          }

                          mCommandsAdapter =
                              new CommandsAdapter(
                                  Commands.getPackageInfo(packageNamePrefix + ".", context));
                          if (isAdded()) {
                            mRecyclerViewCommands.setLayoutManager(
                                new LinearLayoutManager(requireActivity()));
                          }

                          if (isAdded()) {
                            mRecyclerViewCommands.setAdapter(mCommandsAdapter);
                          }
                          mRecyclerViewCommands.setVisibility(View.VISIBLE);
                          mCommandsAdapter.setOnItemClickListener(
                              (command, v) -> {
                                mCommand.setText(
                                    splitCommands[0].contains(" ")
                                        ? splitPrefix(splitCommands[0], 0) + " " + command
                                        : command);
                                mCommand.setSelection(mCommand.getText().length());
                                mRecyclerViewCommands.setVisibility(View.GONE);
                              });
                        } else {
                          mCommandsAdapter =
                              new CommandsAdapter(Commands.getCommand(s.toString(), context));
                          if (isAdded()) {
                            mRecyclerViewCommands.setLayoutManager(
                                new LinearLayoutManager(requireActivity()));
                          }

                          mRecyclerViewCommands.setAdapter(mCommandsAdapter);
                          mRecyclerViewCommands.setVisibility(View.VISIBLE);
                          mCommandsAdapter.setOnItemClickListener(
                              (command, v) -> {
                                mCommand.setText(
                                    command.contains(" <") ? command.split("<")[0] : command);

                                mCommand.setSelection(mCommand.getText().length());
                              });
                        }
                      });

              mCommandInput.setEndIconDrawable(
                  Utils.getDrawable(
                      Utils.isBookmarked(s.toString().trim(), requireActivity())
                          ? R.drawable.ic_bookmark_added
                          : R.drawable.ic_add_bookmark,
                      requireActivity()));

              mCommandInput.setEndIconVisible(true);

              mCommandInput.setEndIconOnClickListener(
                  v -> {
                    if (Utils.isBookmarked(s.toString().trim(), requireActivity())) {
                      Utils.deleteFromBookmark(s.toString().trim(), requireActivity());
                      Utils.snackBar(
                              view,
                              getString(R.string.bookmark_removed_message, s.toString().trim()))
                          .show();
                    } else {
                      Utils.addBookmarkIconOnClickListener(s.toString().trim(), view, context);
                    }
                    mCommandInput.setEndIconDrawable(
                        Utils.getDrawable(
                            Utils.isBookmarked(s.toString().trim(), requireActivity())
                                ? R.drawable.ic_bookmark_added
                                : R.drawable.ic_add_bookmark,
                            requireActivity()));

                    mBookMarks.setVisibility(
                        Utils.getBookmarks(requireActivity()).size() > 0
                            ? View.VISIBLE
                            : View.GONE);
                  });

              mSendButton.setImageDrawable(
                  Utils.getDrawable(R.drawable.ic_send, requireActivity()));
              mSendButton.setOnClickListener(
                  new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                      sendButtonClicked = true;
                      mPasteButton.hide();
                      mUndoButton.hide();
                      if (mRecyclerViewCommands.getVisibility() == View.VISIBLE) {
                        mRecyclerViewCommands.setVisibility(View.GONE);
                      }
                      if (adbConnection != null) {
                        putCommand();
                      } else {

                        mCommandInput.setError(getString(R.string.device_not_connected));
                        mCommandInput.setErrorIconDrawable(
                            Utils.getDrawable(R.drawable.ic_cancel, requireActivity()));
                        mCommandInput.setErrorIconOnClickListener(
                            t -> {
                              mCommand.setText(null);
                            });

                        Utils.alignMargin(mSendButton);
                        Utils.alignMargin(mCable);

                        mHistoryButton.setVisibility(View.VISIBLE);

                        if (mHistory == null) {
                          mHistory = new ArrayList<>();
                        }

                        mHistory.add(mCommand.getText().toString());

                        new MaterialAlertDialogBuilder(requireActivity())
                            .setTitle(requireActivity().getString(R.string.error))
                            .setMessage(requireActivity().getString(R.string.otg_not_connected))
                            .setPositiveButton(
                                requireActivity().getString(R.string.ok),
                                (dialogInterface, i) -> {})
                            .show();
                      }
                    }
                  });
            }
          }
        });

    mBookMarks.setTooltipText(getString(R.string.bookmarks));

    mBookMarks.setOnClickListener(
        v -> {
          Utils.bookmarksDialog(context, requireActivity(), mCommand, mCommandInput, mBookMarks);
        });

    mHistoryButton.setTooltipText(getString(R.string.history));

    mHistoryButton.setOnClickListener(
        v -> {
          PopupMenu popupMenu = new PopupMenu(requireContext(), mCommand);
          Menu menu = popupMenu.getMenu();
          for (int i = 0; i < getRecentCommands().size(); i++) {
            menu.add(Menu.NONE, i, Menu.NONE, getRecentCommands().get(i));
          }
          popupMenu.setOnMenuItemClickListener(
              item -> {
                for (int i = 0; i < getRecentCommands().size(); i++) {
                  if (item.getItemId() == i) {
                    mCommand.setText(getRecentCommands().get(i));
                    mCommand.setSelection(mCommand.getText().length());
                  }
                }
                return false;
              });
          popupMenu.show();
        });

    // Glow otg symbol when adb connection successfull

    mSettingsButton.setTooltipText(getString(R.string.settings));
    mSettingsButton.setOnClickListener(
        v -> {
          Intent settingsIntent = new Intent(requireActivity(), SettingsActivity.class);
          startActivity(settingsIntent);
        });

    handler =
        new Handler(Looper.getMainLooper()) {
          @Override
          public void handleMessage(@NonNull android.os.Message msg) {
            switch (msg.what) {
              case DEVICE_FOUND:
                initCommand();
                if (adbConnection != null) {
                  mCable.setColorFilter(Utils.getColor(R.color.green, requireActivity()));
                }
                if (mWaitingDialog != null) {
                  Toast.makeText(context, "device found", Toast.LENGTH_SHORT).show();
                }
                break;

              case CONNECTING:
             //   Toast.makeText(context, "connecting", Toast.LENGTH_SHORT).show();
              if (adbConnection == null) {
                waitingDialog(context);
              }

                break;

              case DEVICE_NOT_FOUND:
                
                mCable.clearColorFilter();
               // Toast.makeText(context, "device not found!", Toast.LENGTH_SHORT).show();
                adbConnection = null; // Fix this issue
                break;

              case FLASHING:
                Toast.makeText(requireContext(), getString(R.string.flashing), Toast.LENGTH_SHORT)
                    .show();
                break;

              case INSTALLING_PROGRESS:
                Toast.makeText(requireContext(), getString(R.string.progress), Toast.LENGTH_SHORT)
                    .show();
                break;
            }
          }
        };

    /*------------------------------------------------------*/

    AdbBase64 base64 = new OtgUtils.MyAdbBase64();
    try {
      adbCrypto =
          AdbCrypto.loadAdbKeyPair(
              base64,
              new File(requireActivity().getFilesDir(), "private_key"),
              new File(requireActivity().getFilesDir(), "public_key"));
    } catch (Exception e) {
      e.printStackTrace();
    }

    if (adbCrypto == null) {
      try {
        adbCrypto = AdbCrypto.generateAdbKeyPair(base64);
        adbCrypto.saveAdbKeyPair(
            new File(requireActivity().getFilesDir(), "private_key"),
            new File(requireActivity().getFilesDir(), "public_key"));
      } catch (Exception e) {
        Log.w(Const.TAG, getString(R.string.generate_key_failed), e);
      }
    }

    IntentFilter filter = new IntentFilter();
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction(MessageOtg.USB_PERMISSION);

    ContextCompat.registerReceiver(
        requireContext(), mUsbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

    // Check USB
    UsbDevice device = requireActivity().getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
    if (device != null) {
      System.out.println("From Intent!");
      asyncRefreshAdbConnection(device);
    } else {
      System.out.println("From onCreate!");
      for (String k : mManager.getDeviceList().keySet()) {
        UsbDevice usbDevice = mManager.getDeviceList().get(k);
        handler.sendEmptyMessage(CONNECTING);
        if (mManager.hasPermission(usbDevice)) {
          asyncRefreshAdbConnection(usbDevice);
        } else {
          mManager.requestPermission(
              usbDevice,
              PendingIntent.getBroadcast(
                  requireActivity().getApplicationContext(),
                  0,
                  new Intent(MessageOtg.USB_PERMISSION),
                  PendingIntent.FLAG_IMMUTABLE));
        }
      }
    }

    mCommand.setOnEditorActionListener(this);
    mCommand.setOnKeyListener(this);

    return view;
  }

  private void waitingDialog(Context context) {
    View dialogView = LayoutInflater.from(context).inflate(R.layout.loading_dialog_layout, null);
    ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

    mWaitingDialog =
        new MaterialAlertDialogBuilder(context)
            .setCancelable(false)
            .setView(dialogView)
            .setTitle(context.getString(R.string.waiting_device))
            .setPositiveButton(
                getString(R.string.ok),
                (dialogInterface, i) -> {
                  if (mListener != null) {
                    mListener.onRequestReset();
                  }
                })
            .show();
    progressBar.setVisibility(View.VISIBLE);
  }

  private void closeWaiting() {
    if (mWaitingDialog != null && mWaitingDialog.isShowing()) {
      mWaitingDialog.dismiss();
    }
  }

  public void asyncRefreshAdbConnection(final UsbDevice device) {
    if (device != null) {
      new Thread() {
        @Override
        public void run() {
          final UsbInterface intf = findAdbInterface(device);
          try {
            setAdbInterface(device, intf);
          } catch (Exception e) {
            Log.w(Const.TAG, getString(R.string.set_adb_interface_fail), e);
          }
        }
      }.start();
    }
  }

  BroadcastReceiver mUsbReceiver =
      new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          Log.d(Const.TAG, "mUsbReceiver onReceive => " + action);
          if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            String deviceName = device.getDeviceName();
            if (mDevice != null && mDevice.getDeviceName().equals(deviceName)) {
              try {
                Log.d(Const.TAG, "setAdbInterface(null, null)");
                setAdbInterface(null, null);
              } catch (Exception e) {
                Log.w(Const.TAG, "setAdbInterface(null,null) failed", e);
              }
            }
          } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            asyncRefreshAdbConnection(device);
            mListener.onRequestReset();

          } else if (MessageOtg.USB_PERMISSION.equals(action)) {
            System.out.println("From receiver!");
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            handler.sendEmptyMessage(CONNECTING);
            if (mManager.hasPermission(usbDevice)) asyncRefreshAdbConnection(usbDevice);
            else
              mManager.requestPermission(
                  usbDevice,
                  PendingIntent.getBroadcast(
                      requireContext().getApplicationContext(),
                      0,
                      new Intent(MessageOtg.USB_PERMISSION),
                      PendingIntent.FLAG_IMMUTABLE));
          }
        }
      };

  // searches for an adb interface on the given USB device
  private UsbInterface findAdbInterface(UsbDevice device) {
    int count = device.getInterfaceCount();
    for (int i = 0; i < count; i++) {
      UsbInterface intf = device.getInterface(i);
      if (intf.getInterfaceClass() == 255
          && intf.getInterfaceSubclass() == 66
          && intf.getInterfaceProtocol() == 1) {
        return intf;
      }
    }
    return null;
  }

  // Sets the current USB device and interface
  private synchronized boolean setAdbInterface(UsbDevice device, UsbInterface intf)
      throws IOException, InterruptedException {
    if (adbConnection != null) {
      adbConnection.close();
      adbConnection = null;
      mDevice = null;
    }

    if (device != null && intf != null) {
      UsbDeviceConnection connection = mManager.openDevice(device);
      if (connection != null) {
        if (connection.claimInterface(intf, false)) {
          handler.sendEmptyMessage(CONNECTING);
          adbConnection = AdbConnection.create(new UsbChannel(connection, intf), adbCrypto);
          adbConnection.connect();
          // TODO: DO NOT DELETE IT, I CAN'T EXPLAIN WHY
          adbConnection.open("shell:exec date");

          mDevice = device;
          handler.sendEmptyMessage(DEVICE_FOUND);
          return true;
        } else {
          connection.close();
        }
      }
    }

    handler.sendEmptyMessage(DEVICE_NOT_FOUND);

    mDevice = null;
    return false;
  }

  // Define a Handler instance

  private void initCommand() {
    // Open the shell stream of ADB
    logs.setText("");
    try {
      stream = adbConnection.open("shell:");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      return;
    } catch (IOException e) {
      e.printStackTrace();
      return;
    } catch (InterruptedException e) {
      e.printStackTrace();
      return;
    }

    // Start the receiving thread
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                while (!stream.isClosed()) {
                  try {
                    // Print each thing we read from the shell stream
                    final String[] output = {new String(stream.read(), "US-ASCII")};
                    handler.post(
                        new Runnable() {
                          @Override
                          public void run() {
                            if (user == null) {
                              user = output[0].substring(0, output[0].lastIndexOf("/") + 1);
                            } else if (output[0].contains(user)) {
                              System.out.println("End => " + user);
                            }

                            logs.append(output[0]);

                            scrollView.post(
                                new Runnable() {
                                  @Override
                                  public void run() {
                                    scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                                    mCommand.requestFocus();
                                  }
                                });
                          }
                        });
                  } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return;
                  } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                  } catch (IOException e) {
                    e.printStackTrace();
                    return;
                  }
                }
              }
            })
        .start();
  }

  private void putCommand() {

    if (!mCommand.getText().toString().isEmpty()) {
      // We become the sending thread

      try {
        String cmd = mCommand.getText().toString();
        if (cmd.equalsIgnoreCase("clear")) {
          String log = logs.getText().toString();
          String[] logSplit = log.split("\n");
          logs.setText(logSplit[logSplit.length - 1]);
        } else if (cmd.equalsIgnoreCase("exit")) {
          requireActivity().finish();
        } else {
          stream.write((cmd + "\n").getBytes("UTF-8"));
        }
        mCommand.setText("");
      } catch (IOException e) {
        e.printStackTrace();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    } else
      Toast.makeText(requireContext(), getString(R.string.no_command), Toast.LENGTH_SHORT).show();
  }

  public void open(View view) {}

  @Override
  public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    /* We always return false because we want to dismiss the keyboard */
    if (adbConnection != null && actionId == EditorInfo.IME_ACTION_DONE) {
      putCommand();
    }

    return true;
  }

  @Override
  public boolean onKey(View v, int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      /* Just call the onEditorAction function to handle this for us */
      return onEditorAction((TextView) v, EditorInfo.IME_ACTION_DONE, event);
    } else {
      return false;
    }
  }

  private List<String> getRecentCommands() {
    List<String> mRecentCommands = new ArrayList<>(mHistory);
    Collections.reverse(mRecentCommands);
    return mRecentCommands;
  }

  private int lastIndexOf(String s, String splitTxt) {
    return s.lastIndexOf(splitTxt);
  }

  private String splitPrefix(String s, int i) {
    String[] splitPrefix = {s.substring(0, lastIndexOf(s, " ")), s.substring(lastIndexOf(s, " "))};
    return splitPrefix[i].trim();
  }

  private void setVisibilityWithDelay(View view, int delayMillis) {
    new Handler(Looper.getMainLooper())
        .postDelayed(
            () -> {
              view.setVisibility(View.VISIBLE);
            },
            delayMillis);
  }

  @Override
  public void onResume() {
    super.onResume();
    KeyboardUtils.disableKeyboard(context, requireActivity(), view);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (mUsbReceiver != null) {
      requireContext().unregisterReceiver(mUsbReceiver);
    }
    try {
      if (adbConnection != null) {
        adbConnection.close();
        adbConnection = null;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void updateInputField(String sharedText) {
    if (sharedText != null) {
      mCommand.setText(sharedText);
      mCommand.requestFocus();
      mCommand.setSelection(mCommand.getText().length());
    }
  }
}
