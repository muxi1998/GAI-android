//
//  Message.swift
//  BreezeApp
//
//  Created for BreezeApp
//

import Foundation
import SwiftUI

struct Message: Identifiable {
    let id = UUID()
    let content: String
    let isUser: Bool
    let timestamp: Date
    var image: Image?
    
    init(content: String, isUser: Bool, image: Image? = nil) {
        self.content = content
        self.isUser = isUser
        self.timestamp = Date()
        self.image = image
    }
} 