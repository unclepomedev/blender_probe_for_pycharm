# 新しいアドオンプロジェクトの作成

ごくシンプルなアドオンとテストや CI がセットアップされた構成で開発を始められます。

1. **File** > **New Project** を選択します。
2. 左側のジェネレーター一覧から **Blender addon** を選択します。
3. プロジェクトの作成場所を設定し、**Create** をクリックします。

<div>
  <img src="images/wizard.png" alt="Blender addonジェネレーターを使用したNew Projectウィザード" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

Blender 4.2 以降の Extensions に準拠したクリーンなプロジェクト構成が生成されます:

* `my_addon_package/`: アドオンのPythonパッケージ。ウィザードで入力した名前のパッケージが作成されます。
* `tests/`: すぐに実行可能なテストスイート。
* `.github/`: GitHub Actions用のCIワークフロー。
* `LICENSE`: GPLv3ライセンスファイル。
* `pyproject.toml`: Pythonツール設定ファイル。
