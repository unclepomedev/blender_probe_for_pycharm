# 設定

Blender Probe の動作には Blender ランタイムが必要です。そのため、プラグインの各機能を使用する前にランタイムの設定を行ってください。

## オプションA: `blup` による自動設定 🦀

[blup](https://github.com/unclepomedev/blup) で Blender のバージョンを管理している場合、追加の設定は不要です。プラグインがプロジェクトの `.blender-version` ファイルまたはグローバルデフォルトに基づいて、Blender 実行ファイルを自動的に検出します。

（テスト対象の Blender バージョンを頻繁に変える、あるいは型スタブ生成機能のバージョン間 diff を取ることで API 変更を追跡するなどが容易になります。これはデイリービルドに関しても同様であるため、いち早い対応が可能になります。）

## オプションB: 実行バイナリの手動登録と管理

`blup` を使わない場合、あるいは複数の Blender バージョン（安定版、LTS、デイリービルドなど）を用途に応じて素早く切り替えたい場合、設定画面でバイナリを登録・管理できます（PyCharm における Python インタプリタの管理と同様です）。

1. **Settings/Preferences** > **Tools** > **Blender Probe** を開きます。
2. **Configured Blender Executables** のテーブル下の **+** ボタンをクリックし、**Name**（例: `Blender 4.2 LTS`）と **Executable path** を入力して追加します:
   * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.0\blender.exe`
   * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
   * **Linux**: `/usr/bin/blender`
3. **Active Blender executable** ドロップダウンから、現在使用したい Blender 実行ファイルを選択します（`<Auto-detect via blup>` を選択すると `blup` による自動検出が行われます）。
4. **OK** または **Apply** をクリックします。

<div>
  <img src="images/configuration.png" alt="Blender実行ファイルパスの設定" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>

## レガシーアドオンのサポート

* **Launch Blender with `--factory-startup`**（**Settings/Preferences** > **Tools** > **Blender Probe** 内）:
  標準のサポート動作に合わせてデフォルトで有効になっています。ユーザー環境（アドオンディレクトリ）に直接インストールされたサードパーティモジュールに依存している場合のみ無効化してください（`--factory-startup` はそれらのユーザー空間モジュールの読み込みを防ぎます）。これは実行/デバッグおよびテスト実行の両方に適用されます。

  > **警告:** `--factory-startup` を無効化すると、インストール済みのサードパーティ製アドオンもすべて読み込まれるため、Blender の起動時にクラッシュする原因となる可能性があります。自己責任での利用となり、サポート対象外となります。
