import os
import re

TARGET_DIR = r"D:\git\Remember\app\src\main\java\dev\bikram\remember"

def process_file(filepath):
    if "NotesWidget.kt" in filepath or "TapSoundModifiers.kt" in filepath:
        return

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # Replace .clickable { with .tapSoundClickable {
    pattern = r'\.clickable\s*\{'
    if re.search(pattern, content):
        content = re.sub(pattern, '.tapSoundClickable {', content)
        
        # Add import if not exists
        if 'import dev.bikram.remember.ui.feedback.tapSoundClickable' not in content:
            import_idx = content.rfind('\nimport ')
            if import_idx != -1:
                end_of_line = content.find('\n', import_idx + 1)
                imports_str = '\nimport dev.bikram.remember.ui.feedback.tapSoundClickable'
                content = content[:end_of_line] + imports_str + content[end_of_line:]
            else:
                pkg_idx = content.find('\n\n')
                if pkg_idx != -1:
                    imports_str = 'import dev.bikram.remember.ui.feedback.tapSoundClickable\n'
                    content = content[:pkg_idx+2] + imports_str + content[pkg_idx+2:]

    if content != original_content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, _, files in os.walk(TARGET_DIR):
    for file in files:
        if file.endswith(".kt"):
            process_file(os.path.join(root, file))

print("Done")
