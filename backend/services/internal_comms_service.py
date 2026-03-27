import os
import logging
import datetime
from typing import Dict, Any, List, Optional
from PIL import Image, ImageDraw, ImageFont
import imageio
import numpy as np

logger = logging.getLogger(__name__)

class InternalCommsService:
    def __init__(self, output_dir: str = "data/comms"):
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)

    async def generate_3p_update(self, progress: List[str], plans: List[str], problems: List[str]) -> str:
        """Generates a Progress, Plans, Problems (3P) update."""
        timestamp = datetime.datetime.now().strftime("%Y-%m-%d")
        update = f"# Team 3P Update - {timestamp}\n\n"
        
        update += "## 🟢 Progress\n"
        for item in progress:
            update += f"- {item}\n"
        
        update += "\n## 🔵 Plans\n"
        for item in plans:
            update += f"- {item}\n"
            
        update += "\n## 🔴 Problems\n"
        for item in problems:
            update += f"- {item}\n"
            
        filename = f"3P_Update_{timestamp.replace('-', '')}.md"
        file_path = os.path.join(self.output_dir, filename)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(update)
        return update

    async def create_slack_gif(self, text: str, bg_color: str = "#2f3c7e", text_color: str = "#f9e795") -> str:
        """Creates an animated GIF optimized for Slack using PIL and imageio."""
        frames = []
        width, height = 128, 128
        num_frames = 10
        
        for i in range(num_frames):
            frame = Image.new('RGB', (width, height), bg_color)
            draw = ImageDraw.Draw(frame)
            
            # Simple animation: pulse the text size or position
            offset = int(5 * np.sin(i * 2 * np.pi / num_frames))
            
            # Note: Using default font as specific fonts might not be available
            draw.text((width//2 - 20, height//2 - 10 + offset), text, fill=text_color)
            
            # Add some decorative elements
            draw.ellipse([10 + offset, 10, 20 + offset, 20], fill=text_color)
            draw.ellipse([width - 20 - offset, height - 20, width - 10 - offset, height - 10], fill=text_color)
            
            frames.append(np.array(frame))
            
        filename = f"slack_animation_{datetime.datetime.now().strftime('%H%M%S')}.gif"
        file_path = os.path.join(self.output_dir, filename)
        imageio.mimsave(file_path, frames, fps=10)
        return file_path
