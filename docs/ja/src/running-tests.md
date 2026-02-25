# テスト

> **🚀 クイックスタート:** **Project Wizard** を使用した場合、完全に設定済みのテストファイル（`tests/run_tests.py` と `tests/test_sample.py`）が既に含まれています。
>
> 💡 ヒント: 実際の Blender インスタンス上で動作するテストを、PyCharm（TeamCity）にネイティブ統合しつつ、CI でもまったく同じ設定で実行できるようにセットアップすることは、本質的に複雑な設定が必要となります。既存のプロジェクトにテストをセットアップする最も簡単な方法は、一時的なプロジェクトで Project Wizard を使用してテストファイルを生成し、それを自分のプロジェクトにコピー＆ペーストすることです。

## PyCharmネイティブユニットテスト

PyCharm の `unittest` を使用できます。

1.  **Run/Debug Configurations** を開きます。
2.  **+** ボタンをクリックし、**Blender Test** を選択します。
3.  **Name**: 名前を付けます（例: "All Tests"）。
4.  **Test Directory**: テストスクリプトが含まれるフォルダを選択します。
5.  テストフォルダに[このテンプレート](https://github.com/unclepomedev/blender_probe_for_pycharm/blob/0903621d8cd9ea3602a8911d8eda1681b1782361/src/main/resources/fileTemplates/internal/BlenderAddon_RunTests.py.ft)のような `run_tests.py` ファイルを作成します。
6.  **Run** ボタンをクリックします。

<div style="display: flex; align-items: center; justify-content: center; gap: 10px; margin: 20px 0;">
  <div style="width: 40%; display: flex; flex-direction: column; gap: 10px;">
    <img src="images/test1.png" alt="テスト設定への導線" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
    <img src="images/test3.png" alt="テストの実行" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
  </div>
  <div style="width: 60%;">
    <img src="images/test2.png" alt="テスト設定" style="width: 100%; border: 1px solid #ddd; border-radius: 4px;">
  </div>
</div>

### テストの書き方

設定が完了したら、以下のようにテストを実装してTDDを実践できます。

```python
# test_sample.py
import unittest
import bpy
# from my_addon_package import operators  <-- 自動的に動作します！

class TestSampleOperator(unittest.TestCase):
    """
    カスタムオペレーターの統合テスト。
    Blender Probe経由でBlender内で実行されます。
    """

    def setUp(self):
        # 1. Blenderをクリーンな状態にリセット
        bpy.ops.wm.read_homefile(use_empty=True)
        # 2. テストシーンのセットアップ（キューブを作成）
        bpy.ops.mesh.primitive_cube_add()
        self.test_obj = bpy.context.object
        self.test_obj.name = "TestCube"

    def test_operator_logic(self):
        """オペレーターがオブジェクトの名前を変更し、プロパティを追加することを検証"""
        # [Arrange]
        # 初期状態を確認
        self.assertEqual(self.test_obj.name, "TestCube")
        self.assertNotIn("is_processed", self.test_obj)
        # [Act]
        # カスタムオペレーターを実行（例: アドオンで定義されたもの）
        # コンテキストはBlenderが自動的に処理します。
        result = bpy.ops.object.sample_operator()
        # [Assert]
        # 1. 戻り値を確認
        self.assertIn("FINISHED", result)
        # 2. 副作用を確認（ロジックの検証）
        self.assertEqual(self.test_obj.name, "TestCube_processed")
        self.assertTrue(self.test_obj.get("is_processed"))

if __name__ == "__main__":
    unittest.main()
```

## CI

**Blender addon** ウィザードで作成されたプロジェクトには、事前設定済みの GitHub Actions ワークフロー（`.github/workflows/ci.yml`）が含まれています。
* **設定不要:** コードを GitHub にプッシュするだけです。
* **自動テスト:** ワークフローがヘッドレス版の Blender（Linux）を自動的にインストールし、IDE と同じランナーロジックでテストを実行します。
* **リンティング:** Ruff がコードスタイルをチェックします。
* **Dependabot:** アクションと依存関係を最新の状態に保ちます。
