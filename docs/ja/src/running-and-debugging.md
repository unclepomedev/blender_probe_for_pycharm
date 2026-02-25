# アドオンの実行とデバッグ

アドオンを読み込んだ状態でBlenderを起動する、あるいはデバッガーを使用できます。

## セットアップ

1.  **Run/Debug Configurations** を開きます（右上のドロップダウン > Edit Configurations）。
2.  **+** ボタンをクリックし、**Blender** を選択します。
3.  **Name**: 名前を付けます（例: "BlenderRun"）。
4.  **OK** をクリックします。

<p align="center">
  <img src="images/test1.png" alt="Run/Debug Configurationへの導線" width="48%" style="border: 1px solid #ddd; border-radius: 4px;">
  <img src="images/run_config.png" alt="Run/Debug Configuration" width="48%" style="border: 1px solid #ddd; border-radius: 4px;">
</p>

## 実行またはデバッグ

### 実行

1.  **Run** ボタンをクリックするだけです。（プラグインが `blender_manifest.toml` によりアドオンルートを自動検出します。）

<div>
  <img src="images/run.png" alt="アドオンの実行" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

### デバッグ

1.  Pythonコードにブレークポイントを設定します（行番号の横のガターをクリック）。
2.  **Debug**（バグアイコン）ボタンをクリックします。
3.  Blenderが起動し、PyCharmが自動的にアタッチします。ブレークポイントに到達すると、実行が一時停止します。
4.  ビューポートの強制再描画: ブレークポイントで一時停止中に、Blender の UI/ビューポートを強制的に再描画して現在の状態を確認できます。（⚠️ *注意: シーンによっては重い操作になる場合があります。連打は避けてください。*）
    * **Windows / Linux**: `Ctrl + Alt + Shift + D`
    * **macOS**: `Cmd + Opt + Shift + D`

<div>
  <img src="images/debug.png" alt="アドオンのデバッグ" style="border: 1px solid #ddd; border-radius: 4px;">
</div>

## ホットリロード

ホットリロードを使用して変更を即座に反映できるため、UIパネルやオペレーターのコードの変更確認等を素早く行うことができます。

1.  PyCharm の **Run** または **Debug** から、Blender が実行中であるとします。
2.  Python コードを変更します。
3.  以下のいずれかの方法でリロードがトリガーされます。
    * **自動**: PyCharm から Blender に**フォーカスを切り替える**だけです。（**System Settings** > **Autosave** の **Save files when switching to a different application** が有効になっている必要があります。）
    * **手動**: 環境の設定に応じてファイルを手動で保存します。
4.  Blender コンソールまたは PyCharm の通知で確認します。アドオンが更新されたコードで実行されています。
> **注意**: これはアドオンの登録解除、`sys.modules` から関連モジュールのパージ、再登録を行う「ディープリロード」を実行します。ほとんどのコード変更に対応しますが、複雑な状態変更には再起動が必要な場合があります。
