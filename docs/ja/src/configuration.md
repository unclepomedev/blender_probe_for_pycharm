# 設定

Blender Probe の動作には Blender ランタイムが必要です。そのため、プラグインの各機能を使用する前にランタイムの設定を行ってください。

## オプションA: `blup` による自動設定 🦀

[blup](https://github.com/unclepomedev/blup) で Blender のバージョンを管理している場合、追加の設定は不要です。プラグインがプロジェクトの `.blender-version` ファイルまたはグローバルデフォルトに基づいて、Blender 実行ファイルを自動的に検出します。

（テスト対象の Blender バージョンを頻繁に変える、あるいは型スタブ生成機能のバージョン間 diff を取ることで API 変更を追跡するなどが容易になります。これはデイリービルドに関しても同様であるため、いち早い対応が可能になります。）

## オプションB: 実行バイナリの手動登録と管理

`blup` を使わない場合、あるいは複数の Blender バージョン（安定版、LTS、デイリービルドなど）を用途に応じて素早く切り替えたい場合、設定画面でバイナリを登録・管理できます（PyCharm における Python インタプリタの管理と同様です）。

1. **Settings/Preferences** > **Tools** > **Blender Probe** を開きます。
2. **Configured Blender Executables** のテーブル下の **+** ボタンをクリックし、**Name**（例: `Blender 5.2 LTS`）と **Executable path** を入力して追加します:
   * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.2\blender.exe`
   * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
   * **Linux**: `/usr/bin/blender`
3. **Active Blender executable** ドロップダウンから、現在使用したい Blender 実行ファイルを選択します（`<Auto-detect via blup>` を選択すると `blup` による自動検出が行われます）。
4. **OK** または **Apply** をクリックします。

<div>
  <img src="images/configuration.png" alt="Blender実行バイナリ一覧テーブルとアクティブ実行バイナリ選択UI" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

## レガシーアドオンのサポート

* **Launch Blender with `--factory-startup`**（**Settings/Preferences** > **Tools** > **Blender Probe** 内）:
  標準のサポート動作に合わせてデフォルトで有効になっています。アプリ実行、テスト実行、および型スタブ生成時にユーザーの `startup.blend` ファイルの読み込みをスキップし、クリーンで再現性の高い環境を保証します。カスタムの初期設定ファイル読み込みが必要な場合のみ無効化してください。

  > **警告:** `--factory-startup` を無効化すると、カスタマイズされた初期スタートアップ設定が読み込まれるため、初期シーンや状態が変化し、テスト実行やアプリ起動時に予期しない動作を引き起こす可能性があります。
