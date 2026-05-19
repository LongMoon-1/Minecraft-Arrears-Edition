import tkinter as tk
from tkinter import ttk, filedialog
import subprocess
import os
import zipfile
import re
import threading
import sys

class Launcher:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("Minecraft Arrears Edition Launcher")
        self.root.geometry("500x600")
        self.root.resizable(False, False)
        
        self.root.update_idletasks()
        w = self.root.winfo_width()
        h = self.root.winfo_height()
        x = (self.root.winfo_screenwidth() - w) // 2
        y = (self.root.winfo_screenheight() - h) // 2
        self.root.geometry(f"+{x}+{y}")
        
        self.version = self.detect_version()
        self.java_path = "java"
        self.java_paths = []
        
        self.build_ui()
        
        self.root.after(100, self.scan_java_background)
    
    def get_base_dir(self):
        if getattr(sys, 'frozen', False):
            return os.path.dirname(os.path.abspath(sys.executable))
        else:
            return os.path.dirname(os.path.abspath(__file__))
    
    def detect_version(self):
        base_dir = self.get_base_dir()
        jar_file = None
        for f in os.listdir(base_dir):
            if f.endswith(".jar") and f != "lwjgl.jar" and f != "lwjgl_util.jar":
                try:
                    with zipfile.ZipFile(os.path.join(base_dir, f), 'r') as zf:
                        if "Game.class" in zf.namelist():
                            jar_file = f
                            break
                except:
                    pass
        
        if jar_file:
            try:
                with zipfile.ZipFile(os.path.join(base_dir, jar_file), 'r') as zf:
                    data = zf.read("Game.class")
                    matches = re.findall(rb'beta\s*(\d+\.\d+)', data)
                    if matches:
                        return "beta " + matches[0].decode()
            except:
                pass
        
        version_file = os.path.join(base_dir, "data", "version.txt")
        if os.path.exists(version_file):
            with open(version_file, "r") as f:
                return f.read().strip()
        
        return "beta 1.1"
    
    def scan_java_background(self):
        self.status_label.config(text="正在扫描 Java...", fg="blue")
        self.root.update()
        
        paths = set()
        paths.add("java")
        paths.add("java.exe")
        
        java_home = os.environ.get("JAVA_HOME")
        if java_home:
            paths.add(os.path.join(java_home, "bin", "java.exe"))
            paths.add(os.path.join(java_home, "bin", "java"))
        
        base_dirs = ["C:\\Program Files", "C:\\Program Files (x86)"]
        if os.environ.get("ProgramFiles"):
            base_dirs.append(os.environ["ProgramFiles"])
        if os.environ.get("ProgramFiles(x86)"):
            base_dirs.append(os.environ["ProgramFiles(x86)"])
        
        oracle_path = os.path.join("C:\\Program Files", "Common Files", "Oracle", "Java", "javapath", "java.exe")
        if os.path.exists(oracle_path):
            paths.add(oracle_path)
        
        for base in base_dirs:
            if os.path.exists(base):
                try:
                    for d in os.listdir(base):
                        full_dir = os.path.join(base, d)
                        if os.path.isdir(full_dir):
                            exe = os.path.join(full_dir, "bin", "java.exe")
                            if os.path.exists(exe):
                                paths.add(exe)
                except:
                    pass
        
        valid = []
        for p in sorted(paths):
            try:
                result = subprocess.run([p, "-version"], capture_output=True, timeout=3)
                out = (result.stdout + result.stderr).decode("utf-8", errors="replace")
                if "version" in out.lower():
                    ver_match = re.search(r'(\d+\.\d+[\.\d+]*)', out)
                    ver = ver_match.group(1) if ver_match else "?"
                    valid.append((p, ver))
            except:
                pass
        
        if valid:
            self.java_path = valid[0][0]
            self.java_paths = [p for p, _ in valid]
            self.java_var.set(self.java_path)
            self.populate_java_list()
            self.status_label.config(text=f"找到 {len(valid)} 个 Java", fg="green")
        else:
            self.status_label.config(text="未找到 Java，请手动选择", fg="orange")
    
    def build_ui(self):
        title = tk.Label(
            self.root, 
            text="Minecraft Arrears Edition",
            font=("Microsoft YaHei", 18, "bold")
        )
        title.pack(pady=15)
        
        version_label = tk.Label(
            self.root,
            text=f"Version: {self.version}",
            font=("Microsoft YaHei", 10),
            fg="gray"
        )
        version_label.pack(pady=5)
        
        java_frame = tk.LabelFrame(
            self.root,
            text="Java 运行环境",
            font=("Microsoft YaHei", 10),
            padx=10,
            pady=10
        )
        java_frame.pack(fill="x", padx=20, pady=10)
        
        top_row = tk.Frame(java_frame)
        top_row.pack(fill="x")
        
        self.java_var = tk.StringVar(value=self.java_path)
        java_entry = tk.Entry(
            top_row,
            textvariable=self.java_var,
            font=("Microsoft YaHei", 9),
            width=40
        )
        java_entry.pack(side="left", padx=(0, 5))
        
        browse_btn = tk.Button(
            top_row,
            text="浏览",
            font=("Microsoft YaHei", 9),
            command=self.browse_java
        )
        browse_btn.pack(side="left", padx=(0, 5))
        
        scan_btn = tk.Button(
            top_row,
            text="重新扫描",
            font=("Microsoft YaHei", 9),
            command=lambda: threading.Thread(target=self.scan_java_background, daemon=True).start()
        )
        scan_btn.pack(side="left")
        
        list_frame = tk.Frame(java_frame)
        list_frame.pack(fill="x", pady=(5, 0))
        
        scrollbar = tk.Scrollbar(list_frame)
        scrollbar.pack(side="right", fill="y")
        
        self.java_listbox = tk.Listbox(
            list_frame,
            font=("Microsoft YaHei", 9),
            height=4,
            yscrollcommand=scrollbar.set
        )
        self.java_listbox.pack(fill="x")
        scrollbar.config(command=self.java_listbox.yview)
        
        self.java_listbox.bind("<<ListboxSelect>>", self.on_java_select)
        
        self.launch_btn = tk.Button(
            self.root,
            text="启 动 游 戏",
            font=("Microsoft YaHei", 14, "bold"),
            bg="#4CAF50",
            fg="white",
            width=20,
            height=2,
            command=self.launch_game
        )
        self.launch_btn.pack(pady=10)
        
        self.status_label = tk.Label(
            self.root,
            text="正在扫描 Java...",
            font=("Microsoft YaHei", 9),
            fg="blue"
        )
        self.status_label.pack(pady=5)
        
        separator = ttk.Separator(self.root, orient="horizontal")
        separator.pack(fill="x", padx=30, pady=5)
        
        tutorial_frame = tk.LabelFrame(
            self.root,
            text="使用教程",
            font=("Microsoft YaHei", 11, "bold"),
            padx=10,
            pady=10
        )
        tutorial_frame.pack(fill="both", expand=True, padx=20, pady=10)
        
        tutorial_text = tk.Text(
            tutorial_frame,
            font=("Microsoft YaHei", 9),
            wrap="word",
            height=12,
            bg="#f5f5f5"
        )
        tutorial_text.pack(fill="both", expand=True)
        
        tutorial_content = f"""【操作方式】
  W A S D - 移动   |   鼠标 - 视角旋转
  空格 - 跳跃      |   左键 - 破坏方块
  右键 - 放置方块  |   ESC - 保存并退出

【资源包教程】
  1. 在游戏目录创建 data/Material/ 文件夹
  2. 放入 grass.png 和 stone.png（建议16x16）
  3. 启动游戏自动加载，无贴图使用默认纯色

【存档说明】
  存档位置：data/save/save.dat
  退出自动保存，启动自动加载
  重新开始：删除 data/save/ 文件夹

【游戏信息】
  世界：128x128  |  平台：100x100 绿草
  当前版本：{self.version}
"""
        
        tutorial_text.insert("1.0", tutorial_content)
        tutorial_text.config(state="disabled")
        
        footer = tk.Label(
            self.root,
            text="Minecraft Arrears Edition - 凉心制作",
            font=("Microsoft YaHei", 8),
            fg="gray"
        )
        footer.pack(pady=5)
    
    def populate_java_list(self):
        self.java_listbox.delete(0, tk.END)
        for p in self.java_paths:
            try:
                result = subprocess.run([p, "-version"], capture_output=True, timeout=3)
                out = (result.stdout + result.stderr).decode("utf-8", errors="replace")
                ver_match = re.search(r'(\d+\.\d+[\.\d+]*)', out)
                ver = ver_match.group(1) if ver_match else "?"
            except:
                ver = "?"
            self.java_listbox.insert(tk.END, f"Java {ver}  -  {p}")
    
    def on_java_select(self, event):
        selection = self.java_listbox.curselection()
        if selection:
            idx = selection[0]
            if idx < len(self.java_paths):
                self.java_path = self.java_paths[idx]
                self.java_var.set(self.java_path)
    
    def browse_java(self):
        filename = filedialog.askopenfilename(
            title="选择 Java 可执行文件",
            filetypes=[("Java", "java.exe"), ("Java", "java"), ("All files", "*.*")]
        )
        if filename:
            self.java_path = filename
            self.java_var.set(self.java_path)
    
    def launch_game(self):
        self.launch_btn.config(state="disabled", text="游戏运行中...")
        self.status_label.config(text="正在启动...", fg="blue")
        
        thread = threading.Thread(target=self.run_game, daemon=True)
        thread.start()
    
    def run_game(self):
        base_dir = self.get_base_dir()
        
        game_jar = None
        for f in os.listdir(base_dir):
            if f.endswith(".jar") and f != "lwjgl.jar" and f != "lwjgl_util.jar":
                jar_path = os.path.join(base_dir, f)
                try:
                    with zipfile.ZipFile(jar_path, 'r') as zf:
                        if "Game.class" in zf.namelist():
                            game_jar = jar_path
                            break
                except:
                    pass
        
        lwjgl_jar = os.path.join(base_dir, "lwjgl.jar")
        lwjgl_util_jar = os.path.join(base_dir, "lwjgl_util.jar")
        
        if game_jar:
            cp = f"{game_jar};{lwjgl_jar};{lwjgl_util_jar}"
        else:
            cp = f".;{lwjgl_jar};{lwjgl_util_jar}"
        
        try:
            self.root.after(0, lambda: self.status_label.config(text="游戏运行中...", fg="green"))
            
            process = subprocess.Popen(
                [self.java_path, "-cp", cp, f"-Djava.library.path={base_dir}", "Game"],
                cwd=base_dir,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE
            )
            
            exit_code = process.wait()
            stdout, stderr = process.communicate()
            
            if exit_code == 0:
                self.root.after(0, lambda: self.status_label.config(text="游戏已退出", fg="gray"))
                self.root.after(0, lambda: self.launch_btn.config(state="normal", text="启 动 游 戏"))
            else:
                error_msg = stderr.decode("gbk", errors="replace") if stderr else ""
                if stdout:
                    error_msg += "\n" + stdout.decode("gbk", errors="replace")
                self.root.after(0, lambda: self.show_error("游戏崩溃", f"游戏异常退出 (代码: {exit_code})\n\n{error_msg[:500]}"))
                self.root.after(0, lambda: self.status_label.config(text="游戏崩溃", fg="red"))
                self.root.after(0, lambda: self.launch_btn.config(state="normal", text="启 动 游 戏"))
                
        except FileNotFoundError:
            self.root.after(0, lambda: self.show_error("启动失败", f"未找到 Java\n路径: {self.java_path}\n\n请安装 Java 8+\nhttps://www.java.com"))
            self.root.after(0, lambda: self.status_label.config(text="未找到Java", fg="red"))
            self.root.after(0, lambda: self.launch_btn.config(state="normal", text="启 动 游 戏"))
        except Exception as e:
            self.root.after(0, lambda: self.show_error("启动失败", f"发生错误:\n\n{str(e)}"))
            self.root.after(0, lambda: self.status_label.config(text="启动失败", fg="red"))
            self.root.after(0, lambda: self.launch_btn.config(state="normal", text="启 动 游 戏"))
    
    def show_error(self, title, message):
        error_win = tk.Toplevel(self.root)
        error_win.title(title)
        error_win.geometry("450x250")
        error_win.resizable(False, False)
        error_win.transient(self.root)
        error_win.grab_set()
        
        error_win.update_idletasks()
        w = error_win.winfo_width()
        h = error_win.winfo_height()
        x = (error_win.winfo_screenwidth() - w) // 2
        y = (error_win.winfo_screenheight() - h) // 2
        error_win.geometry(f"+{x}+{y}")
        
        tk.Label(
            error_win,
            text=title,
            font=("Microsoft YaHei", 14, "bold"),
            fg="red"
        ).pack(pady=10)
        
        text = tk.Text(
            error_win,
            font=("Microsoft YaHei", 9),
            wrap="word",
            height=8,
            bg="#fff0f0"
        )
        text.insert("1.0", message)
        text.config(state="disabled")
        text.pack(padx=15, pady=5, fill="both", expand=True)
        
        tk.Button(
            error_win,
            text="确定",
            font=("Microsoft YaHei", 10),
            width=10,
            command=error_win.destroy
        ).pack(pady=10)

if __name__ == "__main__":
    app = Launcher()
    app.root.mainloop()