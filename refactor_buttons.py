import os
import re

TARGET_DIR = r"D:\git\Remember\app\src\main\java\dev\bikram\remember"

COMPONENTS = {
    "IconButton": "RememberIconButton",
    "FilledIconButton": "RememberFilledIconButton",
    "FilledTonalIconButton": "RememberFilledTonalIconButton",
    "Button": "RememberButton",
    "TextButton": "RememberTextButton",
    "OutlinedButton": "RememberOutlinedButton",
    "FilledTonalButton": "RememberFilledTonalButton",
    "FloatingActionButton": "RememberFloatingActionButton",
    "ExtendedFloatingActionButton": "RememberExtendedFloatingActionButton",
    "DropdownMenuItem": "RememberDropdownMenuItem",
    "Tab": "RememberTab",
    "FilterChip": "RememberFilterChip",
    "InputChip": "RememberInputChip",
    "Switch": "RememberSwitch",
    "Checkbox": "RememberCheckbox",
    "ToggleButton": "RememberToggleButton",
}

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content
    added_imports = set()

    for old_comp, new_comp in COMPONENTS.items():
        # Clean up fully qualified names first
        fqn_pattern = r'androidx\.compose\.material3\.' + old_comp + r'\s*\('
        if re.search(fqn_pattern, content):
            content = re.sub(fqn_pattern, f'dev.bikram.remember.ui.components.{new_comp}(', content)
            
        pattern = r'(?<!\.)\b' + old_comp + r'\s*\('
        if re.search(pattern, content):
            content = re.sub(pattern, new_comp + '(', content)
            added_imports.add(f"import dev.bikram.remember.ui.components.{new_comp}")

    if content != original_content:
        # Add imports
        if added_imports:
            # Find the last import
            import_idx = content.rfind('\nimport ')
            if import_idx != -1:
                end_of_line = content.find('\n', import_idx + 1)
                imports_str = '\n' + '\n'.join(added_imports)
                content = content[:end_of_line] + imports_str + content[end_of_line:]
            else:
                # No imports, add after package
                pkg_idx = content.find('\n\n')
                if pkg_idx != -1:
                    imports_str = '\n'.join(added_imports) + '\n'
                    content = content[:pkg_idx+2] + imports_str + content[pkg_idx+2:]

        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Updated {filepath}")

for root, _, files in os.walk(TARGET_DIR):
    for file in files:
        if file.endswith(".kt") and file != "RememberInteractive.kt":
            process_file(os.path.join(root, file))

print("Done")
