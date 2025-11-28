package com.xkball.wallpaper.utils;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.logging.LogUtils;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import javax.imageio.ImageIO;

public class TheSystemTray {

    private static final Logger LOGGER = LogUtils.getLogger();
    // 【修复】将 nid 字段改为 public，以便 VanillaUtils.ClientHandler 可以访问其 hWnd 字段来强制失焦。
    public NOTIFYICONDATA nid;

    // 定义菜单项的唯一 ID
    private static final int MENU_ID_SWITCH = 1;
    private static final int MENU_ID_EXIT = 3;
    private static final int MENU_ID_SEPARATOR = 0;

    // JNA Windows API 菜单分隔线常量 (MF_SEPARATOR = 0x800)
    public static final int MF_SEPARATOR = 0x00000800;

    public interface Shell32 extends Library {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean Shell_NotifyIcon(int dwMessage, NOTIFYICONDATA lpData);
    }

    public interface ExtendedUser32 extends User32 {
        ExtendedUser32 INSTANCE = Native.load("user32", ExtendedUser32.class, W32APIOptions.DEFAULT_OPTIONS);
        HICON CreateIconIndirect(WinGDI.ICONINFO piconinfo);
        HMENU CreatePopupMenu();
        boolean AppendMenuW(HMENU menu, int uFlags, WPARAM uIDNewItem, String lpNewItem);
        int TrackPopupMenu(HMENU hMenu, int uFlags, int x, int y, int nReserved, HWND hWnd, RECT prcRect);
    }

    public interface ExtendedGDI32 extends GDI32 {
        ExtendedGDI32 INSTANCE = Native.load("gdi32", ExtendedGDI32.class, W32APIOptions.DEFAULT_OPTIONS);
        WinDef.HBITMAP CreateBitmap(int nWidth, int nHeight, int cPlanes, int cBitsPerPel, byte[] lpvBits);
    }

    public static class NOTIFYICONDATA extends Structure {
        public int cbSize;
        public WinDef.HWND hWnd;
        public int uID;
        public int uFlags;
        public int uCallbackMessage;
        public WinDef.HICON hIcon;
        public char[] szTip = new char[128];
        public int dwState;
        public int dwStateMask;
        public char[] szInfo = new char[256];
        public int uTimeoutOrVersion;
        public char[] szInfoTitle = new char[64];
        public int dwInfoFlags;
        public Guid.GUID guidItem;
        public WinDef.HICON hBalloonIcon;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("cbSize", "hWnd", "uID", "uFlags",
                    "uCallbackMessage", "hIcon", "szTip", "dwState", "dwStateMask",
                    "szInfo", "uTimeoutOrVersion", "szInfoTitle", "dwInfoFlags",
                    "guidItem", "hBalloonIcon");
        }
    }

    public static final int NIM_ADD = 0x00000000;
    public static final int NIM_DELETE = 0x00000002;
    public static final int NIF_MESSAGE = 0x00000001;
    public static final int NIF_ICON = 0x00000002;
    public static final int NIF_TIP = 0x00000004;

    public TheSystemTray() {
        this.nid = createNotifyIconData();
    }

    public void closeTray(){
        // 确保在托盘图标被移除时（即游戏关闭时）执行一次桌面重绘清理，以防万一。
        if (VanillaUtils.ClientHandler.isWindowAsBG()) {
            VanillaUtils.ClientHandler.forceDesktopRedraw();
        }

        User32.INSTANCE.DestroyIcon(nid.hIcon);
        User32.INSTANCE.DestroyWindow(nid.hWnd);
        Shell32.INSTANCE.Shell_NotifyIcon(NIM_DELETE, nid);
    }

    public NOTIFYICONDATA createNotifyIconData() {
        var name = "MinePaper Engine";
        WinDef.HWND hwnd = createMessageWindow(name);
        WinDef.HICON hIcon = createIcon();

        NOTIFYICONDATA nid = new NOTIFYICONDATA();
        nid.cbSize = nid.size();
        nid.hWnd = hwnd;
        nid.uID = 1;
        nid.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
        nid.uCallbackMessage = WinUser.WM_USER + 1;
        nid.hIcon = hIcon;
        System.arraycopy(name.toCharArray(), 0, nid.szTip, 0, name.length());
        boolean success = Shell32.INSTANCE.Shell_NotifyIcon(NIM_ADD, nid);

        return nid;
    }

    private static WinDef.HWND createMessageWindow(String className) {
        final User32 user32 = User32.INSTANCE;
        WinUser.WNDCLASSEX wndclass = new WinUser.WNDCLASSEX();
        wndclass.lpszClassName = className;
        wndclass.lpfnWndProc =(WinUser.WindowProc) (hwnd, uMsg, wParam, lParam) -> {
            //LOGGER.debug("{},{},{},{}",hwnd,uMsg,wParam,lParam);
            //参考WinUser.h
            if (uMsg > 1024) {
                int event = lParam.intValue();
                if(event == 514 || event == 517){
                    showContextMenu(hwnd);
                }
            }
            return user32.DefWindowProc(hwnd, uMsg, wParam, lParam);
        };
        wndclass.hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
        user32.RegisterClassEx(wndclass);
        return user32.CreateWindowEx(0, className, "HiddenTrayWindow",
                0, 0, 0, 0, 0,
                null, null, wndclass.hInstance, null);
    }

    private static void showContextMenu(WinDef.HWND hwnd) {
        WinDef.HMENU hMenu = ExtendedUser32.INSTANCE.CreatePopupMenu();

        // 1. Switch 选项 (ID = 1)
        var success = ExtendedUser32.INSTANCE.AppendMenuW(hMenu, 0, new WinDef.WPARAM(MENU_ID_SWITCH), "切换壁纸模式");
        if(!success) return;

        // 2. 添加分隔线
        ExtendedUser32.INSTANCE.AppendMenuW(hMenu, MF_SEPARATOR, new WinDef.WPARAM(MENU_ID_SEPARATOR), null);

        // 3. Exit 选项 (ID = 3)
        ExtendedUser32.INSTANCE.AppendMenuW(hMenu, 0, new WinDef.WPARAM(MENU_ID_EXIT), "关闭游戏");


        WinDef.POINT pt = new WinDef.POINT();
        ExtendedUser32.INSTANCE.GetCursorPos(pt);
        ExtendedUser32.INSTANCE.SetForegroundWindow(hwnd);

        int cmd = ExtendedUser32.INSTANCE.TrackPopupMenu(
                hMenu, 0x0120,//TPM_RETURNCMD | TPM_LEFTALIGN | TPM_BOTTOMALIGN,
                pt.x, pt.y, 0, hwnd, null
        );

        // 关键：在 JNA 线程中捕获命令，然后提交到 Minecraft 主线程执行
        if(cmd == MENU_ID_SWITCH){
            Minecraft.getInstance().submit(VanillaUtils.ClientHandler::switchWindowState);

            // 【优化】将焦点设置回隐藏的消息窗口，从而使游戏窗口失焦
            ExtendedUser32.INSTANCE.SetForegroundWindow(hwnd);

        } else if (cmd == MENU_ID_EXIT) {
            // 【修改】将清理和停止操作提交到 Minecraft 主线程，
            // 确保先切换回前景模式，然后再停止，以彻底清理桌面残留。
            Minecraft.getInstance().submit(() -> {
                if (VanillaUtils.ClientHandler.isWindowAsBG()) {
                    // 1. 先安全地切换回前景模式，这包括调用 forceDesktopRedraw()
                    VanillaUtils.ClientHandler.cancelWindowAsBG();
                }
                // 2. 然后停止游戏
                Minecraft.getInstance().stop();
            });
        }
    }

    private static WinDef.HICON createIcon() {
        var iconSet = SharedConstants.getCurrentVersion().isStable() ? IconSet.RELEASE : IconSet.SNAPSHOT;
        var iconList = ThrowableSupplier.getOrThrow(() -> iconSet.getStandardIcons(Minecraft.getInstance().getVanillaPackResources()));
        var icon = ThrowableSupplier.getOrThrow(() -> ImageIO.read(iconList.getLast().get()));
        //ThrowableSupplier.getOrThrow(() -> ImageIO.write(icon,".png", Path.of("icon").toFile()));
        var width = icon.getWidth();
        var height = icon.getHeight();
        int[] pixels = new int[width * height];
        icon.getRGB(0, 0, width, height, pixels, 0, width);

        WinDef.HDC hdc = User32.INSTANCE.GetDC(null);


        WinGDI.BITMAPINFO bmi = new WinGDI.BITMAPINFO(3);
        bmi.bmiHeader.biSize = bmi.bmiHeader.size();
        bmi.bmiHeader.biWidth = width;
        bmi.bmiHeader.biHeight = -height;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_BITFIELDS;
        bmi.bmiColors[0] = new WinGDI.RGBQUAD();
        bmi.bmiColors[0].rgbRed = (byte)0xFF;
        bmi.bmiColors[1] = new WinGDI.RGBQUAD();
        bmi.bmiColors[1].rgbGreen = (byte)0xFF;
        bmi.bmiColors[2] = new WinGDI.RGBQUAD();
        bmi.bmiColors[2].rgbBlue = (byte)0xFF;

        PointerByReference ppvBits = new PointerByReference();
        WinDef.HBITMAP colorBitmap = ExtendedGDI32.INSTANCE.CreateDIBSection(hdc, bmi, WinGDI.DIB_RGB_COLORS, ppvBits, null, 0);

        ppvBits.getValue().write(0, pixels, 0, pixels.length);


        byte[] maskBits = new byte[(width * height) / 8];
        WinDef.HBITMAP maskBitmap = ExtendedGDI32.INSTANCE.CreateBitmap(width, height, 1, 1, maskBits);


        WinGDI.ICONINFO iconInfo = new WinGDI.ICONINFO();
        iconInfo.fIcon = true;
        iconInfo.hbmMask = maskBitmap;
        iconInfo.hbmColor = colorBitmap;


        WinDef.HICON hIcon = ExtendedUser32.INSTANCE.CreateIconIndirect(iconInfo);

        GDI32.INSTANCE.DeleteObject(colorBitmap);
        GDI32.INSTANCE.DeleteObject(maskBitmap);
        User32.INSTANCE.ReleaseDC(null, hdc);

        return hIcon;
    }
}