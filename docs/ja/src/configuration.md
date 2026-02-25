# 設定

Blender Probe の動作には Blender ランタイムが必要です。そのため、プラグインの各機能を使用する前にランタイムの設定を行ってください。

## オプションA: `blup` による自動設定 🦀

[blup](https://github.com/unclepomedev/blup) で Blender のバージョンを管理している場合、追加の設定は不要です。プラグインがプロジェクトの `.blender-version` ファイルまたはグローバルデフォルトに基づいて、Blender 実行ファイルを自動的に検出します。

（テスト対象の Blender バージョンを頻繁に変える、あるいは型スタブ生成機能のバージョン間 diff を取ることで API 変更を追跡するなどが容易になります。これはデイリービルドに関しても同様であるため、いち早い対応が可能になります。）

## オプションB: 手動パス設定

`blup` を使わない場合、プラグインを使用する前にBlender実行ファイルのパスを設定する必要があります。

1.  **Settings/Preferences** > **Tools** > **Blender Probe** を開きます。
2.  **Blender Executable Path** を設定します:
    * **Windows**: `C:\Program Files\Blender Foundation\Blender 5.0\blender.exe`
    * **macOS**: `/Applications/Blender.app/Contents/MacOS/Blender`
    * **Linux**: `/usr/bin/blender`
3.  **OK** をクリックします。

<div>
  <img src="images/configuration.png" alt="Blender実行ファイルパスの設定" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
</div>
