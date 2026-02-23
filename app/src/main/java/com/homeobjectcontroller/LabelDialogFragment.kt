package com.homeobjectcontroller

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LabelDialogFragment : DialogFragment() {

    private var onLabelSaved: ((String) -> Unit)? = null
    private var onLabelDeleted: (() -> Unit)? = null

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_EXISTING_LABEL = "existing_label"

        fun newInstance(
            category: String,
            existingLabel: String? = null,
            onSave: (String) -> Unit,
            onDelete: () -> Unit
        ): LabelDialogFragment {
            return LabelDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                    putString(ARG_EXISTING_LABEL, existingLabel)
                }
                onLabelSaved = onSave
                onLabelDeleted = onDelete
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val category = arguments?.getString(ARG_CATEGORY) ?: "Object"
        val existingLabel = arguments?.getString(ARG_EXISTING_LABEL)

        val view = layoutInflater.inflate(R.layout.dialog_label, null)
        val editText = view.findViewById<EditText>(R.id.labelEditText)
        editText.hint = "Detected: $category"
        existingLabel?.let { editText.setText(it) }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.label_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val label = editText.text.toString().trim()
                if (label.isNotEmpty()) {
                    onLabelSaved?.invoke(label)
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (existingLabel != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                onLabelDeleted?.invoke()
            }
        }

        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }
}
