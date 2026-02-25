# コードスタブの生成（自動補完）

`bpy` やその他の Blender モジュールのコード補完を有効にするために:

1.  PyCharm でプロジェクトを開きます。
2.  **Tools** > **Regenerate Blender Stubs** を選択します。
3.  プログレスバーが完了するまで待ちます。プロジェクトルートに隠しフォルダ `.blender_stubs` が作成され、自動的に Source Root としてマークされます。

<div>
  <img src="images/generate_stubs.png" alt="Regenerate Blender Stubsアクションを表示するPyCharmメニュー" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

> **💡 ヒント:** `.blender_stubs` フォルダには生成されたファイルが含まれており、バージョン管理する必要はありません。プロジェクトの `.gitignore` ファイルに `.blender_stubs/` を追加することを推奨します。
> *（**Blender addon** ウィザードでプロジェクトを作成した場合、これは既に設定済みです。）*
> **💡 ヒント:** Blender の API は非常に動的であるため、PyCharm が自動的に型を推論できない場合があります。完全な自動補完を得るには、**型ヒント**を使用してください:
> ```python
> def my_func(context: bpy.types.Context):
>     obj: bpy.types.Object = context.active_object
>     print(obj.location) # 自動補完が機能します
> ```
> **💡 ヒント:** 生成されたスタブがニーズに合わない場合は、`.blender_stubs` を削除して `fake-bpy-module` などの静的スタブを使用することもできます。
> **💡 ヒント:** 異なる環境や新しいBlenderバージョン間で一貫した型チェック（mypy/pyright/pyrefly/ty）を行うために、`.blender_stubs` ディレクトリをgitリポジトリに含めることも選択肢になりえます（その際には、用途に応じ `.gitattributes` を設定することを推奨します）。一方で、そのような目的がない場合は、前述の通り `.blender_stubs` を `.gitignore` に追加してください。
