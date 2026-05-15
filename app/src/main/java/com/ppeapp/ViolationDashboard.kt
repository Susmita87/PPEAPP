package com.ppeapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory

@Composable
fun ViolationDashboard(
    violations: List<ViolationRecord>,
    onClose: () -> Unit,
    onDeleteOld: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Safety Dashboard",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onClose) {
                Text("Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDeleteOld,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete Violations Older than 24h", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (violations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No violations recorded", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(violations) { violation ->
                    ViolationItem(violation)
                }
            }
        }
    }
}

@Composable
fun ViolationItem(violation: ViolationRecord) {
    val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(violation.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            val file = File(violation.imagePath)
            if (file.exists()) {
                val bitmap = remember(violation.imagePath) {
                    BitmapFactory.decodeFile(violation.imagePath)
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Image Not Stored", color = Color.White, fontSize = 10.sp)
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    violation.type,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text("Time: $date", color = Color.Gray, fontSize = 12.sp)
                Text("ID: #${violation.trackId}", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
